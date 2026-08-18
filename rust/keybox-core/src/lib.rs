// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#![forbid(unsafe_code)]

use p256::pkcs8::{DecodePrivateKey, EncodePrivateKey, EncodePublicKey};
use p256::SecretKey as P256SecretKey;
use rsa::pkcs1::DecodeRsaPrivateKey;
use rsa::{RsaPrivateKey, RsaPublicKey};
use std::fmt;
use zeroize::Zeroizing;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum KeyboxError {
    UnsupportedAlgorithm,
    InvalidPrivateKey,
    EncodingFailed,
}

impl fmt::Display for KeyboxError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::UnsupportedAlgorithm => "unsupported keybox algorithm",
            Self::InvalidPrivateKey => "invalid keybox private key",
            Self::EncodingFailed => "keybox private key encoding failed",
        })
    }
}

impl std::error::Error for KeyboxError {}

pub fn normalize_private_key_pkcs8(
    algorithm: &str,
    private_key_pem: &str,
) -> Result<Zeroizing<Vec<u8>>, KeyboxError> {
    if private_key_pem.is_empty() {
        return Err(KeyboxError::InvalidPrivateKey);
    }
    if algorithm.eq_ignore_ascii_case("EC") || algorithm.eq_ignore_ascii_case("ECDSA") {
        normalize_ec_private_key(private_key_pem)
    } else if algorithm.eq_ignore_ascii_case("RSA") {
        normalize_rsa_private_key(private_key_pem)
    } else {
        Err(KeyboxError::UnsupportedAlgorithm)
    }
}

pub fn public_key_spki_from_pkcs8(
    algorithm: &str,
    private_key_pkcs8: &[u8],
) -> Result<Vec<u8>, KeyboxError> {
    if private_key_pkcs8.is_empty() {
        return Err(KeyboxError::InvalidPrivateKey);
    }
    if algorithm.eq_ignore_ascii_case("EC") || algorithm.eq_ignore_ascii_case("ECDSA") {
        let key = P256SecretKey::from_pkcs8_der(private_key_pkcs8)
            .map_err(|_| KeyboxError::InvalidPrivateKey)?;
        key.public_key()
            .to_public_key_der()
            .map(|document| document.as_bytes().to_vec())
            .map_err(|_| KeyboxError::EncodingFailed)
    } else if algorithm.eq_ignore_ascii_case("RSA") {
        let key = RsaPrivateKey::from_pkcs8_der(private_key_pkcs8)
            .map_err(|_| KeyboxError::InvalidPrivateKey)?;
        RsaPublicKey::from(&key)
            .to_public_key_der()
            .map(|document| document.as_bytes().to_vec())
            .map_err(|_| KeyboxError::EncodingFailed)
    } else {
        Err(KeyboxError::UnsupportedAlgorithm)
    }
}

fn canonicalize_private_key_pem(private_key_pem: &str) -> Result<Zeroizing<String>, KeyboxError> {
    // CertHack's managed oracle calls trimLine() before PEMParser: trim outer whitespace,
    // then trim every line independently while preserving line boundaries. xml-core preserves
    // interior indentation, so mirror that exact adapter behavior before RustCrypto sees the PEM.
    let trimmed = private_key_pem.trim();
    if trimmed.is_empty() {
        return Err(KeyboxError::InvalidPrivateKey);
    }

    let mut canonical = Zeroizing::new(String::new());
    canonical
        .try_reserve_exact(trimmed.len())
        .map_err(|_| KeyboxError::EncodingFailed)?;
    for (index, line) in trimmed.split('\n').enumerate() {
        if index != 0 {
            canonical.push('\n');
        }
        canonical.push_str(line.trim());
    }
    Ok(canonical)
}

fn normalize_ec_private_key(private_key_pem: &str) -> Result<Zeroizing<Vec<u8>>, KeyboxError> {
    let canonical = canonicalize_private_key_pem(private_key_pem)?;
    let key = P256SecretKey::from_sec1_pem(canonical.as_str())
        .or_else(|_| P256SecretKey::from_pkcs8_pem(canonical.as_str()))
        .map_err(|_| KeyboxError::InvalidPrivateKey)?;
    let encoded = key
        .to_pkcs8_der()
        .map_err(|_| KeyboxError::EncodingFailed)?;
    Ok(Zeroizing::new(encoded.as_bytes().to_vec()))
}

fn normalize_rsa_private_key(private_key_pem: &str) -> Result<Zeroizing<Vec<u8>>, KeyboxError> {
    let canonical = canonicalize_private_key_pem(private_key_pem)?;
    let key = RsaPrivateKey::from_pkcs1_pem(canonical.as_str())
        .or_else(|_| RsaPrivateKey::from_pkcs8_pem(canonical.as_str()))
        .map_err(|_| KeyboxError::InvalidPrivateKey)?;
    let encoded = key
        .to_pkcs8_der()
        .map_err(|_| KeyboxError::EncodingFailed)?;
    Ok(Zeroizing::new(encoded.as_bytes().to_vec()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use cleverestricky_xml_core::parse_keybox_xml_bytes;

    const VALID_EC: &[u8] =
        include_bytes!("../../../service/src/test/resources/keybox/valid_ec.xml");

    #[test]
    fn shared_ec_fixture_normalizes_to_pkcs8_der() {
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let key = &document.keys[0];
        let normalized = normalize_private_key_pkcs8(&key.algorithm, &key.private_key_pem).unwrap();
        assert!(!normalized.is_empty());
        P256SecretKey::from_pkcs8_der(normalized.as_slice()).unwrap();
        let spki = public_key_spki_from_pkcs8(&key.algorithm, normalized.as_slice()).unwrap();
        assert!(!spki.is_empty());
    }

    #[test]
    fn canonicalization_matches_managed_trim_line_behavior() {
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let canonical = canonicalize_private_key_pem(&document.keys[0].private_key_pem).unwrap();
        let lines = canonical.lines().collect::<Vec<_>>();
        assert_eq!(
            lines.first().copied(),
            Some("-----BEGIN EC PRIVATE KEY-----")
        );
        assert_eq!(lines.last().copied(), Some("-----END EC PRIVATE KEY-----"));
        assert!(lines.iter().all(|line| *line == line.trim()));
    }

    #[test]
    fn algorithm_matching_is_legacy_compatible() {
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let pem = &document.keys[0].private_key_pem;
        assert!(normalize_private_key_pkcs8("EC", pem).is_ok());
        assert!(normalize_private_key_pkcs8("ecdsa", pem).is_ok());
        assert_eq!(
            normalize_private_key_pkcs8("Ed25519", pem).unwrap_err(),
            KeyboxError::UnsupportedAlgorithm
        );
    }

    #[test]
    fn malformed_private_key_fails_closed() {
        assert_eq!(
            normalize_private_key_pkcs8("EC", "not a key").unwrap_err(),
            KeyboxError::InvalidPrivateKey
        );
        assert_eq!(
            normalize_private_key_pkcs8("RSA", "not a key").unwrap_err(),
            KeyboxError::InvalidPrivateKey
        );
        assert_eq!(
            public_key_spki_from_pkcs8("EC", b"not a key").unwrap_err(),
            KeyboxError::InvalidPrivateKey
        );
    }
}
