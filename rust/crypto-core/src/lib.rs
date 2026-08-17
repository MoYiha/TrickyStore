// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#![forbid(unsafe_code)]

use aes_gcm::aead::{AeadInPlace, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce, Tag};
use base64::Engine as _;
use p256::ecdsa::{Signature as EcSignature, VerifyingKey as EcVerifyingKey};
use pbkdf2::pbkdf2_hmac;
use rsa::pkcs1v15::{Signature as RsaSignature, VerifyingKey as RsaVerifyingKey};
use rsa::pkcs8::DecodePublicKey;
use rsa::RsaPublicKey;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use signature::hazmat::PrehashVerifier;
use std::fmt;
use zeroize::Zeroize;

const KDF_ITERATIONS: u32 = 250_000;
const KEY_BYTES: usize = 32;
const SALT_BYTES: usize = 16;
const IV_BYTES: usize = 12;
const TAG_BYTES: usize = 16;
const HEADER_BYTES: usize = 4 + 4 + SALT_BYTES + IV_BYTES;
const VERSION_LEGACY: u32 = 1;
const VERSION_CURRENT: u32 = 2;

const CBOX_MAGIC: [u8; 4] = *b"CBOX";
const CTSB_MAGIC: [u8; 4] = *b"CTSB";
const CBOX_SIGNATURE_V2_DOMAIN: &[u8] = b"CBOX-SIGNATURE-V2\0";
const MAX_CBOX_CIPHERTEXT_BYTES: usize = 10 * 1024 * 1024;
const MAX_CBOX_XML_UTF16_UNITS: usize = 10 * 1024 * 1024;
const MAX_CBOX_SIGNATURE_UTF16_UNITS: usize = 16 * 1024;
const MAX_CBOX_PUBLIC_KEY_UTF16_UNITS: usize = 16 * 1024;
const MAX_CBOX_AUTHOR_UTF16_UNITS: usize = 1024;
const MAX_PASSWORD_UTF16_UNITS: usize = 1024;
const MAX_BACKUP_PLAINTEXT_BYTES: usize = 32 * 1024 * 1024;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CboxPayload {
    pub author: String,
    pub xml_content: String,
    pub signature_base64: String,
    pub signature_version: u8,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CryptoError {
    InvalidInput,
    UnsupportedVersion,
    AuthenticationFailed,
    InvalidPayload,
    RandomUnavailable,
}

impl fmt::Display for CryptoError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::InvalidInput => "invalid encrypted input",
            Self::UnsupportedVersion => "unsupported encrypted format version",
            Self::AuthenticationFailed => "encrypted input authentication failed",
            Self::InvalidPayload => "decrypted payload is invalid",
            Self::RandomUnavailable => "secure randomness is unavailable",
        })
    }
}

impl std::error::Error for CryptoError {}

#[derive(Deserialize)]
struct CboxJson {
    author: String,
    xml_content: String,
    signature: String,
    #[serde(default = "default_signature_version")]
    signature_version: SignatureVersion,
}

#[derive(Deserialize)]
#[serde(untagged)]
enum SignatureVersion {
    Number(i64),
    Text(String),
}

impl Default for SignatureVersion {
    fn default() -> Self {
        Self::Number(1)
    }
}

fn default_signature_version() -> SignatureVersion {
    SignatureVersion::default()
}

impl SignatureVersion {
    fn normalized(&self) -> Option<u8> {
        let value = match self {
            Self::Number(value) => *value,
            Self::Text(value) => value.parse::<i64>().ok()?,
        };
        u8::try_from(value)
            .ok()
            .filter(|value| (1..=2).contains(value))
    }
}

pub fn has_supported_cbox_header(bytes: &[u8]) -> bool {
    bytes.len() >= HEADER_BYTES + TAG_BYTES
        && bytes[..4] == CBOX_MAGIC
        && matches!(
            read_u32_be(bytes, 4),
            Some(VERSION_LEGACY | VERSION_CURRENT)
        )
}

pub fn decrypt_cbox(mut bytes: Vec<u8>, password: &str) -> Result<CboxPayload, CryptoError> {
    let result = decrypt_cbox_inner(&mut bytes, password);
    bytes.zeroize();
    result
}

fn decrypt_cbox_inner(bytes: &mut [u8], password: &str) -> Result<CboxPayload, CryptoError> {
    if utf16_units(password) > MAX_PASSWORD_UTF16_UNITS
        || bytes.len() < HEADER_BYTES + TAG_BYTES
        || bytes.len() > HEADER_BYTES + MAX_CBOX_CIPHERTEXT_BYTES
        || bytes[..4] != CBOX_MAGIC
    {
        return Err(CryptoError::InvalidInput);
    }
    let version = read_version(bytes)?;
    let plaintext_range = decrypt_body_in_place(bytes, password, version)?;
    let decoded: CboxJson =
        serde_json::from_slice(&bytes[plaintext_range]).map_err(|_| CryptoError::InvalidPayload)?;
    let signature_version = decoded
        .signature_version
        .normalized()
        .ok_or(CryptoError::InvalidPayload)?;
    if utf16_units(&decoded.author) > MAX_CBOX_AUTHOR_UTF16_UNITS
        || utf16_units(&decoded.xml_content) > MAX_CBOX_XML_UTF16_UNITS
        || utf16_units(&decoded.signature) > MAX_CBOX_SIGNATURE_UTF16_UNITS
    {
        return Err(CryptoError::InvalidPayload);
    }
    Ok(CboxPayload {
        author: decoded.author,
        xml_content: decoded.xml_content,
        signature_base64: decoded.signature,
        signature_version,
    })
}

pub fn verify_cbox_signature(payload: &CboxPayload, public_key_base64: &str) -> bool {
    if !(1..=2).contains(&payload.signature_version)
        || utf16_units(&payload.author) > MAX_CBOX_AUTHOR_UTF16_UNITS
        || utf16_units(&payload.xml_content) > MAX_CBOX_XML_UTF16_UNITS
        || utf16_units(&payload.signature_base64) > MAX_CBOX_SIGNATURE_UTF16_UNITS
        || utf16_units(public_key_base64) > MAX_CBOX_PUBLIC_KEY_UTF16_UNITS
    {
        return false;
    }

    let mut public_key_bytes =
        match base64::engine::general_purpose::STANDARD.decode(public_key_base64) {
            Ok(bytes) => bytes,
            Err(_) => return false,
        };
    let mut signature_bytes =
        match base64::engine::general_purpose::STANDARD.decode(&payload.signature_base64) {
            Ok(bytes) => bytes,
            Err(_) => {
                public_key_bytes.zeroize();
                return false;
            }
        };

    let verified = verify_cbox_signature_der(payload, &public_key_bytes, &signature_bytes);
    public_key_bytes.zeroize();
    signature_bytes.zeroize();
    verified
}

fn verify_cbox_signature_der(
    payload: &CboxPayload,
    public_key_der: &[u8],
    signature_der: &[u8],
) -> bool {
    let digest = match cbox_signature_digest(payload) {
        Some(digest) => digest,
        None => return false,
    };

    if let Ok(public_key) = RsaPublicKey::from_public_key_der(public_key_der) {
        let signature = match RsaSignature::try_from(signature_der) {
            Ok(signature) => signature,
            Err(_) => return false,
        };
        return RsaVerifyingKey::<Sha256>::new(public_key)
            .verify_prehash(&digest, &signature)
            .is_ok();
    }

    let public_key = match EcVerifyingKey::from_public_key_der(public_key_der) {
        Ok(public_key) => public_key,
        Err(_) => return false,
    };
    let signature = match EcSignature::from_der(signature_der) {
        Ok(signature) => signature,
        Err(_) => return false,
    };
    public_key.verify_prehash(&digest, &signature).is_ok()
}

fn cbox_signature_digest(payload: &CboxPayload) -> Option<[u8; 32]> {
    let author = payload.author.as_bytes();
    let xml = payload.xml_content.as_bytes();
    let mut digest = Sha256::new();
    if payload.signature_version == 1 {
        digest.update(author);
        digest.update(xml);
    } else if payload.signature_version == 2 {
        let author_len = u32::try_from(author.len()).ok()?;
        let xml_len = u32::try_from(xml.len()).ok()?;
        digest.update(CBOX_SIGNATURE_V2_DOMAIN);
        digest.update(author_len.to_be_bytes());
        digest.update(author);
        digest.update(xml_len.to_be_bytes());
        digest.update(xml);
    } else {
        return None;
    }
    Some(digest.finalize().into())
}

pub fn decrypt_backup(mut bytes: Vec<u8>, password: &str) -> Result<Vec<u8>, CryptoError> {
    if utf16_units(password) > MAX_PASSWORD_UTF16_UNITS
        || bytes.len() < HEADER_BYTES + TAG_BYTES
        || bytes.len() > HEADER_BYTES + MAX_BACKUP_PLAINTEXT_BYTES + TAG_BYTES
        || bytes[..4] != CTSB_MAGIC
    {
        bytes.zeroize();
        return Err(CryptoError::InvalidInput);
    }
    let version = match read_version(&bytes) {
        Ok(version) => version,
        Err(error) => {
            bytes.zeroize();
            return Err(error);
        }
    };
    let plaintext_range = match decrypt_body_in_place(&mut bytes, password, version) {
        Ok(range) => range,
        Err(error) => {
            bytes.zeroize();
            return Err(error);
        }
    };
    let plaintext_len = plaintext_range.len();
    bytes.copy_within(plaintext_range, 0);
    bytes.truncate(plaintext_len);
    Ok(bytes)
}

pub fn encrypt_backup(plaintext: &[u8], password: &str) -> Result<Vec<u8>, CryptoError> {
    encrypt_backup_owned(plaintext.to_vec(), password)
}

pub fn encrypt_backup_owned(
    mut plaintext: Vec<u8>,
    password: &str,
) -> Result<Vec<u8>, CryptoError> {
    if plaintext.len() > MAX_BACKUP_PLAINTEXT_BYTES
        || utf16_units(password) > MAX_PASSWORD_UTF16_UNITS
    {
        plaintext.zeroize();
        return Err(CryptoError::InvalidInput);
    }
    let mut salt = [0u8; SALT_BYTES];
    let mut iv = [0u8; IV_BYTES];
    if getrandom::getrandom(&mut salt).is_err() || getrandom::getrandom(&mut iv).is_err() {
        plaintext.zeroize();
        salt.zeroize();
        iv.zeroize();
        return Err(CryptoError::RandomUnavailable);
    }
    let result = encrypt_backup_owned_with_nonce(plaintext, password, &salt, &iv);
    salt.zeroize();
    iv.zeroize();
    result
}

#[cfg(test)]
fn encrypt_backup_with_nonce(
    plaintext: &[u8],
    password: &str,
    salt: &[u8; SALT_BYTES],
    iv: &[u8; IV_BYTES],
) -> Result<Vec<u8>, CryptoError> {
    encrypt_backup_owned_with_nonce(plaintext.to_vec(), password, salt, iv)
}

fn encrypt_backup_owned_with_nonce(
    mut output: Vec<u8>,
    password: &str,
    salt: &[u8; SALT_BYTES],
    iv: &[u8; IV_BYTES],
) -> Result<Vec<u8>, CryptoError> {
    let plaintext_len = output.len();
    let extra = match HEADER_BYTES.checked_add(TAG_BYTES) {
        Some(extra) => extra,
        None => {
            output.zeroize();
            return Err(CryptoError::InvalidInput);
        }
    };
    let final_len = match plaintext_len.checked_add(extra) {
        Some(final_len) => final_len,
        None => {
            output.zeroize();
            return Err(CryptoError::InvalidInput);
        }
    };
    if plaintext_len > MAX_BACKUP_PLAINTEXT_BYTES
        || utf16_units(password) > MAX_PASSWORD_UTF16_UNITS
        || output.try_reserve_exact(extra).is_err()
    {
        output.zeroize();
        return Err(CryptoError::InvalidInput);
    }
    output.resize(final_len, 0);
    output.copy_within(0..plaintext_len, HEADER_BYTES);

    let (header, tail) = output.split_at_mut(HEADER_BYTES);
    let (body, tag_output) = tail.split_at_mut(plaintext_len);
    header[..4].copy_from_slice(&CTSB_MAGIC);
    header[4..8].copy_from_slice(&VERSION_CURRENT.to_be_bytes());
    header[8..8 + SALT_BYTES].copy_from_slice(salt);
    header[8 + SALT_BYTES..].copy_from_slice(iv);

    let mut key = [0u8; KEY_BYTES];
    derive_key(password, salt, &mut key);
    let cipher = match Aes256Gcm::new_from_slice(&key) {
        Ok(cipher) => cipher,
        Err(_) => {
            key.zeroize();
            output.zeroize();
            return Err(CryptoError::InvalidInput);
        }
    };
    key.zeroize();

    let nonce = Nonce::from_slice(iv);
    let tag = match cipher.encrypt_in_place_detached(nonce, header, body) {
        Ok(tag) => tag,
        Err(_) => {
            output.zeroize();
            return Err(CryptoError::AuthenticationFailed);
        }
    };
    tag_output.copy_from_slice(tag.as_ref());
    Ok(output)
}

fn decrypt_body_in_place(
    bytes: &mut [u8],
    password: &str,
    version: u32,
) -> Result<std::ops::Range<usize>, CryptoError> {
    let body_end = bytes
        .len()
        .checked_sub(TAG_BYTES)
        .ok_or(CryptoError::InvalidInput)?;
    if body_end < HEADER_BYTES {
        return Err(CryptoError::InvalidInput);
    }

    let mut header = [0u8; HEADER_BYTES];
    header.copy_from_slice(&bytes[..HEADER_BYTES]);
    let mut salt = [0u8; SALT_BYTES];
    salt.copy_from_slice(&header[8..8 + SALT_BYTES]);
    let mut iv = [0u8; IV_BYTES];
    iv.copy_from_slice(&header[8 + SALT_BYTES..]);
    let mut tag_bytes = [0u8; TAG_BYTES];
    tag_bytes.copy_from_slice(&bytes[body_end..]);

    let mut key = [0u8; KEY_BYTES];
    derive_key(password, &salt, &mut key);
    let cipher = match Aes256Gcm::new_from_slice(&key) {
        Ok(cipher) => cipher,
        Err(_) => {
            key.zeroize();
            salt.zeroize();
            iv.zeroize();
            tag_bytes.zeroize();
            header.zeroize();
            return Err(CryptoError::InvalidInput);
        }
    };
    key.zeroize();

    let nonce = Nonce::from_slice(&iv);
    let tag = Tag::from_slice(&tag_bytes);
    let aad: &[u8] = if version == VERSION_CURRENT {
        &header
    } else {
        &[]
    };
    let decrypted =
        cipher.decrypt_in_place_detached(nonce, aad, &mut bytes[HEADER_BYTES..body_end], tag);

    salt.zeroize();
    iv.zeroize();
    tag_bytes.zeroize();
    header.zeroize();
    decrypted.map_err(|_| CryptoError::AuthenticationFailed)?;
    Ok(HEADER_BYTES..body_end)
}

fn derive_key(password: &str, salt: &[u8], output: &mut [u8; KEY_BYTES]) {
    pbkdf2_hmac::<Sha256>(password.as_bytes(), salt, KDF_ITERATIONS, output);
}

fn read_version(bytes: &[u8]) -> Result<u32, CryptoError> {
    match read_u32_be(bytes, 4) {
        Some(VERSION_LEGACY) => Ok(VERSION_LEGACY),
        Some(VERSION_CURRENT) => Ok(VERSION_CURRENT),
        Some(_) => Err(CryptoError::UnsupportedVersion),
        None => Err(CryptoError::InvalidInput),
    }
}

fn read_u32_be(bytes: &[u8], offset: usize) -> Option<u32> {
    let input = bytes.get(offset..offset.checked_add(4)?)?;
    Some(u32::from_be_bytes(input.try_into().ok()?))
}

fn utf16_units(value: &str) -> usize {
    value.encode_utf16().count()
}

#[cfg(test)]
mod tests {
    use super::*;

    const PASSWORD: &str = "correct horse battery staple";
    const CTSB_V1: &str = "Q1RTQgAAAAEAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPBYdDdFyqlYeaU/mul01QMGsRn7g0MjLdOskpN97GWZ5fNXsQE5H+FldOlDg4HvENUIQC5reyWvff34y4iedwVcfVEtlNB";
    const CTSB_V2: &str = "Q1RTQgAAAAIAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPBYdDdFyqlYeaU/mul01QMGsRn7g0MjLdOskpN97GWZ5fNXsQE5H+FldOlDg4HvENUIQC5rexM7K0B5tNer0Cjko6vCq2Z";
    const CBOX_V1: &str = "Q0JPWAAAAAEAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPWcdbGETfpef7mviy130oMGrIv/EwTlOVOuFIH5qfAaY+XUMc2qXWTgNu7FkkT/w9lEwrpv/iFQNyu/EsamoACXPaOVKKg+oGNsVLwNRNN4Gth46JQOziUU1/B3Fen+4BvKg9VtB9H4xnPi4AX+qMZHYhaW8ysgOQaSFcJy59C9IckzAalbsWXcjdsX8r1kr/KBOEALbqGa941n5vAlQEX5P77BBTF";
    const CBOX_V2: &str = "Q0JPWAAAAAIAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobQrPWcdbGETfpef7mviy130oMGrIv/EwTlOVOuFIH5qfAaY+XUMc2qXWTgNu7FkkT/w9lEwrpv/iFQNyu/EsamoACXPaOVKKg+oGNsVLwNRNN4Gth46JQOziUU1/B3Fen+4BvKg9VtB9H4xnPi4AX+qMZHYhaW8ysgOQaSFcJy59C9IckzAalbsWXcjdsX8r1kr/KBOEALbqYmlPfNbKQEZdEZacWRvO3";
    const UNICODE_CTSB_V2: &str = "Q1RTQgAAAAIAAQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobPQ68RCKNeVd3YNhSJdkhC80HQxAsoEMGtGESspVQFfc=";
    const RSA_PUBLIC_KEY: &str = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyC0vjOhT7Q5iJs2Kz0hH7d3jNZTbdYKUz7a9r4mu5YnlWpFpNgECL9r2ES/3KlI1EWDR/kSWnbP+zhuXHb/eXo9gDHQTx1G9EKqBHxi++gXhoHirdBTZfRR1nypLFxFar5iKv6QlPIpb8etuvV8lrysmC+nV3temRsrohibenakmqzw8yvnugIY6M2rF21v/2HYdj3BRKvniIK2/sQWPR8FsHwe22TdmOZscVEtN4t5p+PI2A9A9ICvNqo1NgdJt3fEw3TI55MdgZg3CwG4QwTqQ2+0Cc7Svm+QXAAMfFGyilRJT/qquGlZFUWVldA69hQx+uu2jcns9NhGSIZuiDwIDAQAB";
    const RSA_V2_SIGNATURE: &str = "L0YXhejiCDuGNNTOUp3cjeHsYcEAEUa4CMXWZdHKKIRoVfeQ98h+6ebiXHImw3Ebc1z6PZEcJM1t4m/KZryWKKHdeqxxid0XlZyRg3KnFJd2i5Klz8R8B9gD7i+EfzWD+s1XiGPi/Sb1j1GfNgp+tsGLlj91GwZtNGJaLkKNBoyb2DBrP3vF9XnDSpo1jJBi6sa63c8sDCyyT3CoobYCcdbnc1+W2ggWYat5gy9NxY3aTt0B0aJC97E0v//QjCJToVDU38ty531IWiMNHdpBtbeXDlQfgIn+bg9dFVcYzFSzpUNxGye2N635Hz5WCUkTALnhJERHkWRlsMELmq2pSg==";
    const EC_PUBLIC_KEY: &str = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1JxPSOlrdyKm0raMMZTeiV0WevPD6Nol0UdzGsWfpkwkz8HS3WaT1weN7FrMFimvq4QUJq9pwZ0hrO6/cy++Pg==";
    const EC_V1_SIGNATURE: &str = "MEQCICfCTlaCRDuo9cg1SFXnf/u4Qict9SOgM3u28HoXNYtpAiBOkVG0WCmDfgMMe3Z0qIO/RtgB0D5Ca4B0IWM3CY+VMA==";

    fn decode(value: &str) -> Vec<u8> {
        base64::engine::general_purpose::STANDARD
            .decode(value)
            .unwrap()
    }

    #[test]
    fn backup_goldens_match_managed_oracle() {
        let expected = b"{\"version\":1,\"files\":{\"target.txt\":\"com.example.app\\n\"}}";
        assert_eq!(decrypt_backup(decode(CTSB_V1), PASSWORD).unwrap(), expected);
        assert_eq!(decrypt_backup(decode(CTSB_V2), PASSWORD).unwrap(), expected);
        assert_eq!(
            decrypt_backup(decode(UNICODE_CTSB_V2), "pässwörd🔐").unwrap(),
            b"unicode-password"
        );
    }

    #[test]
    fn cbox_goldens_match_managed_oracle() {
        for encoded in [CBOX_V1, CBOX_V2] {
            let bytes = decode(encoded);
            assert!(has_supported_cbox_header(&bytes));
            let payload = decrypt_cbox(bytes, PASSWORD).unwrap();
            assert_eq!(payload.author, "CleveresTricky golden");
            assert_eq!(
                payload.xml_content,
                "<AndroidAttestation NumberOfKeyboxes=\"0\"></AndroidAttestation>"
            );
            assert!(payload.signature_base64.is_empty());
            assert_eq!(payload.signature_version, 2);
        }
    }

    #[test]
    fn cbox_rsa_v2_signature_matches_managed_oracle() {
        let payload = CboxPayload {
            author: "Δ-author".to_string(),
            xml_content: "<AndroidAttestation NumberOfKeyboxes=\"0\"/>".to_string(),
            signature_base64: RSA_V2_SIGNATURE.to_string(),
            signature_version: 2,
        };
        assert!(verify_cbox_signature(&payload, RSA_PUBLIC_KEY));

        let mut tampered = payload.clone();
        tampered.author.push('!');
        assert!(!verify_cbox_signature(&tampered, RSA_PUBLIC_KEY));
        assert!(!verify_cbox_signature(&payload, EC_PUBLIC_KEY));
    }

    #[test]
    fn cbox_ecdsa_v1_signature_matches_managed_oracle() {
        let payload = CboxPayload {
            author: "Δ-author".to_string(),
            xml_content: "<AndroidAttestation NumberOfKeyboxes=\"0\"/>".to_string(),
            signature_base64: EC_V1_SIGNATURE.to_string(),
            signature_version: 1,
        };
        assert!(verify_cbox_signature(&payload, EC_PUBLIC_KEY));

        let mut tampered = payload.clone();
        tampered.xml_content.push(' ');
        assert!(!verify_cbox_signature(&tampered, EC_PUBLIC_KEY));
        assert!(!verify_cbox_signature(&payload, RSA_PUBLIC_KEY));
    }

    #[test]
    fn backup_writer_emits_current_authenticated_format() {
        let salt: [u8; SALT_BYTES] = core::array::from_fn(|index| index as u8);
        let iv: [u8; IV_BYTES] = core::array::from_fn(|index| (index + 16) as u8);
        let plaintext = b"{\"version\":1,\"files\":{\"target.txt\":\"com.example.app\\n\"}}";
        let encrypted = encrypt_backup_with_nonce(plaintext, PASSWORD, &salt, &iv).unwrap();
        assert_eq!(encrypted, decode(CTSB_V2));
    }

    #[test]
    fn owned_backup_writer_reuses_the_input_allocation_when_capacity_allows() {
        let mut plaintext = Vec::with_capacity(256);
        plaintext.extend_from_slice(b"small backup payload");
        let pointer = plaintext.as_ptr();
        let encrypted = encrypt_backup_owned(plaintext, PASSWORD).unwrap();
        assert_eq!(encrypted.as_ptr(), pointer);
        assert_eq!(
            decrypt_backup(encrypted, PASSWORD).unwrap(),
            b"small backup payload"
        );
    }

    #[test]
    fn tampering_wrong_password_and_truncation_fail_closed() {
        let mut backup = decode(CTSB_V2);
        backup[8] ^= 1;
        assert_eq!(
            decrypt_backup(backup, PASSWORD).unwrap_err(),
            CryptoError::AuthenticationFailed
        );

        let mut cbox = decode(CBOX_V2);
        let index = cbox.len() - TAG_BYTES - 1;
        cbox[index] ^= 1;
        assert_eq!(
            decrypt_cbox(cbox, PASSWORD).unwrap_err(),
            CryptoError::AuthenticationFailed
        );
        assert_eq!(
            decrypt_cbox(decode(CBOX_V2), "wrong password").unwrap_err(),
            CryptoError::AuthenticationFailed
        );
        let mut truncated = decode(CBOX_V2);
        truncated.truncate(HEADER_BYTES + TAG_BYTES - 1);
        assert_eq!(
            decrypt_cbox(truncated, PASSWORD).unwrap_err(),
            CryptoError::InvalidInput
        );
    }

    #[test]
    fn bounds_are_checked_before_crypto_work() {
        assert!(!has_supported_cbox_header(&[]));
        assert_eq!(
            decrypt_cbox(vec![0u8; HEADER_BYTES + TAG_BYTES], PASSWORD).unwrap_err(),
            CryptoError::InvalidInput
        );
        let huge_password = "🔐".repeat(MAX_PASSWORD_UTF16_UNITS / 2 + 1);
        assert_eq!(
            decrypt_backup(decode(CTSB_V2), &huge_password).unwrap_err(),
            CryptoError::InvalidInput
        );

        let payload = CboxPayload {
            author: String::new(),
            xml_content: String::new(),
            signature_base64: String::new(),
            signature_version: 3,
        };
        assert!(!verify_cbox_signature(&payload, RSA_PUBLIC_KEY));
    }
}
