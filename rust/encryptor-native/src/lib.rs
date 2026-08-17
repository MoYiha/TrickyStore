// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#![deny(unsafe_code)]

use aes_gcm::aead::{AeadInPlace, KeyInit};
use aes_gcm::{Aes256Gcm, Nonce};
use cleverestricky_service_core::secure_fs::TrustedDir;
use cleverestricky_xml_core::parse_keybox_xml_bytes;
use jni::objects::{JByteArray, JCharArray, JObject, JString};
use jni::sys::{jboolean, jbyteArray, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use pbkdf2::pbkdf2_hmac;
use serde::Serialize;
use sha2::Sha256;
use std::path::Path;
use std::ptr;
use std::str;
use std::{io, panic};
use zeroize::{Zeroize, Zeroizing};

const KDF_ITERATIONS: u32 = 250_000;
const KEY_BYTES: usize = 32;
const SALT_BYTES: usize = 16;
const IV_BYTES: usize = 12;
const TAG_BYTES: usize = 16;
const HEADER_BYTES: usize = 4 + 4 + SALT_BYTES + IV_BYTES;
const CBOX_MAGIC: [u8; 4] = *b"CBOX";
const CBOX_VERSION_LEGACY: u32 = 1;
const CBOX_VERSION: u32 = 2;
const SIGNATURE_VERSION: u8 = 2;
const MAX_CBOX_CIPHERTEXT_BYTES: usize = 10 * 1024 * 1024;
const MAX_CBOX_PLAINTEXT_BYTES: usize = MAX_CBOX_CIPHERTEXT_BYTES - TAG_BYTES;
const MAX_CBOX_WIRE_BYTES: usize = HEADER_BYTES + MAX_CBOX_CIPHERTEXT_BYTES;
const MAX_XML_BYTES: usize = 10 * 1024 * 1024;
const MAX_AUTHOR_UTF16_UNITS: usize = 1024;
const MAX_AUTHOR_UTF8_BYTES: usize = 4 * MAX_AUTHOR_UTF16_UNITS;
const MAX_SIGNATURE_BYTES: usize = 16 * 1024;
const MIN_PASSWORD_UTF16_UNITS: usize = 12;
const MAX_PASSWORD_UTF16_UNITS: usize = 1024;
const MAX_FILENAME_UTF16_UNITS: usize = 128;
const MAX_DIRECTORY_UTF16_UNITS: usize = 4096;
const VAULT_DIR: &str = "vault";
const VAULT_DIR_MODE: u32 = 0o700;
const CBOX_FILE_MODE: u32 = 0o600;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum EncryptError {
    InvalidInput,
    RandomUnavailable,
    EncryptionFailed,
    StorageFailed,
}

#[derive(Serialize)]
struct CboxJson<'a> {
    author: &'a str,
    signature: &'a str,
    signature_version: u8,
    xml_content: &'a str,
}

pub fn encrypt_cbox_v2(
    author_bytes: &[u8],
    xml_bytes: &[u8],
    signature_base64: &[u8],
    password: &str,
) -> Result<Vec<u8>, EncryptError> {
    let mut salt = [0u8; SALT_BYTES];
    let mut iv = [0u8; IV_BYTES];
    if getrandom::fill(&mut salt).is_err() || getrandom::fill(&mut iv).is_err() {
        salt.zeroize();
        iv.zeroize();
        return Err(EncryptError::RandomUnavailable);
    }
    let result = encrypt_cbox_v2_with_nonce(
        author_bytes,
        xml_bytes,
        signature_base64,
        password,
        &salt,
        &iv,
    );
    salt.zeroize();
    iv.zeroize();
    result
}

fn encrypt_cbox_v2_with_nonce(
    author_bytes: &[u8],
    xml_bytes: &[u8],
    signature_base64: &[u8],
    password: &str,
    salt: &[u8; SALT_BYTES],
    iv: &[u8; IV_BYTES],
) -> Result<Vec<u8>, EncryptError> {
    if author_bytes.is_empty()
        || author_bytes.len() > MAX_AUTHOR_UTF8_BYTES
        || xml_bytes.is_empty()
        || xml_bytes.len() > MAX_XML_BYTES
        || signature_base64.len() > MAX_SIGNATURE_BYTES
        || !(MIN_PASSWORD_UTF16_UNITS..=MAX_PASSWORD_UTF16_UNITS).contains(&utf16_units(password))
    {
        return Err(EncryptError::InvalidInput);
    }

    // Parse again in the native write path. UI pre-validation is not a security boundary.
    parse_keybox_xml_bytes(xml_bytes).map_err(|_| EncryptError::InvalidInput)?;

    let author = str::from_utf8(author_bytes).map_err(|_| EncryptError::InvalidInput)?;
    let xml = str::from_utf8(xml_bytes).map_err(|_| EncryptError::InvalidInput)?;
    let signature = str::from_utf8(signature_base64).map_err(|_| EncryptError::InvalidInput)?;
    if author.trim().is_empty()
        || utf16_units(author) > MAX_AUTHOR_UTF16_UNITS
        || !signature.bytes().all(|byte| byte.is_ascii())
    {
        return Err(EncryptError::InvalidInput);
    }

    let mut plaintext = Zeroizing::new(
        serde_json::to_vec(&CboxJson {
            author,
            signature,
            signature_version: SIGNATURE_VERSION,
            xml_content: xml,
        })
        .map_err(|_| EncryptError::InvalidInput)?,
    );
    if plaintext.len() > MAX_CBOX_PLAINTEXT_BYTES {
        return Err(EncryptError::InvalidInput);
    }

    let mut header = [0u8; HEADER_BYTES];
    header[..4].copy_from_slice(&CBOX_MAGIC);
    header[4..8].copy_from_slice(&CBOX_VERSION.to_be_bytes());
    header[8..8 + SALT_BYTES].copy_from_slice(salt);
    header[8 + SALT_BYTES..].copy_from_slice(iv);

    let mut key = Zeroizing::new([0u8; KEY_BYTES]);
    pbkdf2_hmac::<Sha256>(password.as_bytes(), salt, KDF_ITERATIONS, &mut *key);
    let cipher = Aes256Gcm::new_from_slice(&*key).map_err(|_| EncryptError::EncryptionFailed)?;
    let tag = cipher
        .encrypt_in_place_detached(Nonce::from_slice(iv), &header, &mut plaintext)
        .map_err(|_| EncryptError::EncryptionFailed)?;

    let output_len = HEADER_BYTES
        .checked_add(plaintext.len())
        .and_then(|value| value.checked_add(TAG_BYTES))
        .filter(|value| *value <= MAX_CBOX_WIRE_BYTES)
        .ok_or(EncryptError::InvalidInput)?;
    let mut output = Vec::new();
    output
        .try_reserve_exact(output_len)
        .map_err(|_| EncryptError::InvalidInput)?;
    output.extend_from_slice(&header);
    output.extend_from_slice(&plaintext);
    output.extend_from_slice(tag.as_ref());
    header.zeroize();
    Ok(output)
}

pub fn encrypt_and_save(
    no_backup_dir: &str,
    filename: &str,
    author_bytes: &[u8],
    xml_bytes: &[u8],
    signature_base64: &[u8],
    password: &str,
) -> Result<(), EncryptError> {
    if !valid_filename(filename) {
        return Err(EncryptError::InvalidInput);
    }
    let vault = open_vault(no_backup_dir)?;
    let mut ciphertext = Zeroizing::new(encrypt_cbox_v2(
        author_bytes,
        xml_bytes,
        signature_base64,
        password,
    )?);
    let result = vault
        .atomic_write(filename, &ciphertext, CBOX_FILE_MODE)
        .map_err(map_storage_error);
    ciphertext.zeroize();
    result
}

pub fn ensure_vault(no_backup_dir: &str) -> Result<(), EncryptError> {
    open_vault(no_backup_dir).map(|_| ())
}

pub fn store_encrypted(
    no_backup_dir: &str,
    filename: &str,
    ciphertext: &[u8],
) -> Result<(), EncryptError> {
    if !valid_filename(filename) || !has_supported_cbox_header(ciphertext) {
        return Err(EncryptError::InvalidInput);
    }
    open_vault(no_backup_dir)?
        .atomic_write(filename, ciphertext, CBOX_FILE_MODE)
        .map_err(map_storage_error)
}

pub fn read_encrypted(
    no_backup_dir: &str,
    filename: &str,
) -> Result<Zeroizing<Vec<u8>>, EncryptError> {
    if !valid_filename(filename) {
        return Err(EncryptError::InvalidInput);
    }
    let bytes = open_vault(no_backup_dir)?
        .read_bounded(filename, MAX_CBOX_WIRE_BYTES)
        .map_err(map_storage_error)?;
    if !has_supported_cbox_header(&bytes) {
        return Err(EncryptError::InvalidInput);
    }
    Ok(Zeroizing::new(bytes))
}

pub fn delete_encrypted(no_backup_dir: &str, filename: &str) -> Result<bool, EncryptError> {
    if !valid_filename(filename) {
        return Err(EncryptError::InvalidInput);
    }
    open_vault(no_backup_dir)?
        .unlink_file(filename)
        .map_err(map_storage_error)
}

fn open_vault(no_backup_dir: &str) -> Result<TrustedDir, EncryptError> {
    if no_backup_dir.is_empty() || utf16_units(no_backup_dir) > MAX_DIRECTORY_UTF16_UNITS {
        return Err(EncryptError::InvalidInput);
    }
    let root = TrustedDir::open(Path::new(no_backup_dir)).map_err(map_storage_error)?;
    root.mkdir_child(VAULT_DIR, VAULT_DIR_MODE)
        .map_err(map_storage_error)
}

fn valid_filename(filename: &str) -> bool {
    !filename.is_empty()
        && filename.ends_with(".cbox")
        && !filename.starts_with('.')
        && utf16_units(filename) <= MAX_FILENAME_UTF16_UNITS
        && !filename.contains('/')
        && !filename.contains('\0')
        && filename != "."
        && filename != ".."
}

fn has_supported_cbox_header(bytes: &[u8]) -> bool {
    bytes.len() >= HEADER_BYTES + TAG_BYTES
        && bytes.len() <= MAX_CBOX_WIRE_BYTES
        && bytes[..4] == CBOX_MAGIC
        && matches!(
            read_u32_be(bytes, 4),
            Some(CBOX_VERSION_LEGACY | CBOX_VERSION)
        )
}

fn read_u32_be(bytes: &[u8], offset: usize) -> Option<u32> {
    let input = bytes.get(offset..offset.checked_add(4)?)?;
    Some(u32::from_be_bytes(input.try_into().ok()?))
}

fn map_storage_error(_: io::Error) -> EncryptError {
    EncryptError::StorageFailed
}

fn utf16_units(value: &str) -> usize {
    value.encode_utf16().count()
}

fn read_bytes_bounded(
    env: &mut JNIEnv<'_>,
    input: &JByteArray<'_>,
    max_bytes: usize,
) -> Result<Zeroizing<Vec<u8>>, EncryptError> {
    let length = env
        .get_array_length(input)
        .map_err(|_| EncryptError::InvalidInput)?;
    let length = usize::try_from(length).map_err(|_| EncryptError::InvalidInput)?;
    if length > max_bytes {
        return Err(EncryptError::InvalidInput);
    }
    env.convert_byte_array(input)
        .map(Zeroizing::new)
        .map_err(|_| EncryptError::InvalidInput)
}

fn read_password(
    env: &mut JNIEnv<'_>,
    input: &JCharArray<'_>,
) -> Result<Zeroizing<String>, EncryptError> {
    let length = env
        .get_array_length(input)
        .map_err(|_| EncryptError::InvalidInput)?;
    let length = usize::try_from(length).map_err(|_| EncryptError::InvalidInput)?;
    if !(MIN_PASSWORD_UTF16_UNITS..=MAX_PASSWORD_UTF16_UNITS).contains(&length) {
        return Err(EncryptError::InvalidInput);
    }
    let mut units = Zeroizing::new(vec![0u16; length]);
    env.get_char_array_region(input, 0, &mut units)
        .map_err(|_| EncryptError::InvalidInput)?;
    String::from_utf16(&units)
        .map(Zeroizing::new)
        .map_err(|_| EncryptError::InvalidInput)
}

fn read_string_bounded(
    env: &mut JNIEnv<'_>,
    input: &JString<'_>,
    max_utf16_units: usize,
) -> Result<Zeroizing<String>, EncryptError> {
    let java = env
        .get_string(input)
        .map_err(|_| EncryptError::InvalidInput)?;
    let value: String = java.into();
    if utf16_units(&value) > max_utf16_units {
        return Err(EncryptError::InvalidInput);
    }
    Ok(Zeroizing::new(value))
}

fn throw_native_failure(env: &mut JNIEnv<'_>) {
    let _ = env.throw_new(
        "java/lang/IllegalStateException",
        "Native keybox operation failed",
    );
}

// JNI requires stable exported symbol names. The unsafe attribute is confined to
// the symbol export itself; these functions contain no unsafe block and every
// panic is contained before returning through the JNI ABI.
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_cleveres_tricky_encryptor_NativeCrypto_validateKeyboxXml(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    xml: JByteArray<'_>,
) -> jboolean {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let xml = read_bytes_bounded(&mut env, &xml, MAX_XML_BYTES)?;
        parse_keybox_xml_bytes(&xml).map_err(|_| EncryptError::InvalidInput)?;
        Ok::<(), EncryptError>(())
    }));
    if matches!(result, Ok(Ok(()))) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_cleveres_tricky_encryptor_NativeCrypto_encryptAndSave(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    no_backup_dir: JString<'_>,
    filename: JString<'_>,
    author: JByteArray<'_>,
    xml: JByteArray<'_>,
    signature_base64: JByteArray<'_>,
    password: JCharArray<'_>,
) -> jboolean {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let no_backup_dir =
            read_string_bounded(&mut env, &no_backup_dir, MAX_DIRECTORY_UTF16_UNITS)?;
        let filename = read_string_bounded(&mut env, &filename, MAX_FILENAME_UTF16_UNITS)?;
        let author = read_bytes_bounded(&mut env, &author, MAX_AUTHOR_UTF8_BYTES)?;
        let xml = read_bytes_bounded(&mut env, &xml, MAX_XML_BYTES)?;
        let signature_base64 =
            read_bytes_bounded(&mut env, &signature_base64, MAX_SIGNATURE_BYTES)?;
        let password = read_password(&mut env, &password)?;
        encrypt_and_save(
            &no_backup_dir,
            &filename,
            &author,
            &xml,
            &signature_base64,
            &password,
        )
    }));
    match result {
        Ok(Ok(())) => JNI_TRUE,
        Ok(Err(_)) | Err(_) => {
            throw_native_failure(&mut env);
            JNI_FALSE
        }
    }
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_cleveres_tricky_encryptor_NativeCrypto_ensureVault(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    no_backup_dir: JString<'_>,
) -> jboolean {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let no_backup_dir =
            read_string_bounded(&mut env, &no_backup_dir, MAX_DIRECTORY_UTF16_UNITS)?;
        ensure_vault(&no_backup_dir)
    }));
    if matches!(result, Ok(Ok(()))) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_cleveres_tricky_encryptor_NativeCrypto_storeEncrypted(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    no_backup_dir: JString<'_>,
    filename: JString<'_>,
    ciphertext: JByteArray<'_>,
) -> jboolean {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let no_backup_dir =
            read_string_bounded(&mut env, &no_backup_dir, MAX_DIRECTORY_UTF16_UNITS)?;
        let filename = read_string_bounded(&mut env, &filename, MAX_FILENAME_UTF16_UNITS)?;
        let ciphertext = read_bytes_bounded(&mut env, &ciphertext, MAX_CBOX_WIRE_BYTES)?;
        store_encrypted(&no_backup_dir, &filename, &ciphertext)
    }));
    if matches!(result, Ok(Ok(()))) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_cleveres_tricky_encryptor_NativeCrypto_readEncrypted(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    no_backup_dir: JString<'_>,
    filename: JString<'_>,
) -> jbyteArray {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let no_backup_dir =
            read_string_bounded(&mut env, &no_backup_dir, MAX_DIRECTORY_UTF16_UNITS)?;
        let filename = read_string_bounded(&mut env, &filename, MAX_FILENAME_UTF16_UNITS)?;
        let bytes = read_encrypted(&no_backup_dir, &filename)?;
        let output = env
            .byte_array_from_slice(&bytes)
            .map_err(|_| EncryptError::StorageFailed)?;
        Ok::<jbyteArray, EncryptError>(output.into_raw())
    }));
    match result {
        Ok(Ok(output)) => output,
        Ok(Err(_)) | Err(_) => ptr::null_mut(),
    }
}

#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_cleveres_tricky_encryptor_NativeCrypto_deleteEncrypted(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    no_backup_dir: JString<'_>,
    filename: JString<'_>,
) -> jboolean {
    let result = panic::catch_unwind(panic::AssertUnwindSafe(|| {
        let no_backup_dir =
            read_string_bounded(&mut env, &no_backup_dir, MAX_DIRECTORY_UTF16_UNITS)?;
        let filename = read_string_bounded(&mut env, &filename, MAX_FILENAME_UTF16_UNITS)?;
        delete_encrypted(&no_backup_dir, &filename)
    }));
    match result {
        Ok(Ok(true)) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use cleverestricky_crypto_core::{decrypt_cbox, has_supported_cbox_header as shared_header};

    const VALID_XML: &[u8] = br#"<AndroidAttestation>
<NumberOfKeyboxes>1</NumberOfKeyboxes>
<Keybox>
<Key algorithm="EC">
<PrivateKey>PRIVATE</PrivateKey>
<CertificateChain>
<NumberOfCertificates>1</NumberOfCertificates>
<Certificate>CERTIFICATE</Certificate>
</CertificateChain>
</Key>
</Keybox>
</AndroidAttestation>"#;

    #[test]
    fn deterministic_v2_output_round_trips_through_shared_crypto_core() {
        let salt = [0x11u8; SALT_BYTES];
        let iv = [0x22u8; IV_BYTES];
        let password = "correct horse battery staple";
        let output = encrypt_cbox_v2_with_nonce(
            b"mobile-author",
            VALID_XML,
            b"ZHVtbXktc2lnbmF0dXJl",
            password,
            &salt,
            &iv,
        )
        .unwrap();
        assert!(shared_header(&output));
        let payload = decrypt_cbox(output, password).unwrap();
        assert_eq!(payload.author, "mobile-author");
        assert_eq!(payload.xml_content.as_bytes(), VALID_XML);
        assert_eq!(payload.signature_base64, "ZHVtbXktc2lnbmF0dXJl");
        assert_eq!(payload.signature_version, 2);
    }

    #[test]
    fn malformed_xml_and_passwords_fail_closed() {
        let salt = [0u8; SALT_BYTES];
        let iv = [1u8; IV_BYTES];
        assert_eq!(
            encrypt_cbox_v2_with_nonce(b"", VALID_XML, b"", "123456789012", &salt, &iv),
            Err(EncryptError::InvalidInput)
        );
        assert_eq!(
            encrypt_cbox_v2_with_nonce(b"author", VALID_XML, b"", "short", &salt, &iv),
            Err(EncryptError::InvalidInput)
        );
        let dtd = br#"<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><AndroidAttestation><NumberOfKeyboxes>0</NumberOfKeyboxes></AndroidAttestation>"#;
        assert_eq!(
            encrypt_cbox_v2_with_nonce(b"author", dtd, b"", "123456789012", &salt, &iv),
            Err(EncryptError::InvalidInput)
        );
    }

    #[test]
    fn current_header_is_authenticated() {
        let salt = [2u8; SALT_BYTES];
        let iv = [3u8; IV_BYTES];
        let password = "123456789012";
        let mut output =
            encrypt_cbox_v2_with_nonce(b"author", VALID_XML, b"", password, &salt, &iv).unwrap();
        output[8] ^= 1;
        assert!(decrypt_cbox(output, password).is_err());
    }

    #[test]
    fn stored_ciphertext_header_is_bounded_and_versioned() {
        let salt = [4u8; SALT_BYTES];
        let iv = [5u8; IV_BYTES];
        let output =
            encrypt_cbox_v2_with_nonce(b"author", VALID_XML, b"", "123456789012", &salt, &iv)
                .unwrap();
        assert!(has_supported_cbox_header(&output));
        assert!(!has_supported_cbox_header(b"CBOX"));
    }

    #[test]
    fn filenames_are_single_bounded_cbox_components() {
        assert!(valid_filename("alice.cbox"));
        assert!(!valid_filename("../alice.cbox"));
        assert!(!valid_filename("nested/alice.cbox"));
        assert!(!valid_filename(".hidden.cbox"));
        assert!(!valid_filename("alice.xml"));
    }
}
