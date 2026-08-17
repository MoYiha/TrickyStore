// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use base64::engine::general_purpose::STANDARD;
use base64::Engine as _;
use cleverestricky_certificate_core::{SigningAlgorithm, MAX_CERTIFICATE_DER_BYTES};
use cleverestricky_keybox_core::{normalize_private_key_pkcs8, public_key_spki_from_pkcs8};
use cleverestricky_xml_core::{KeyboxDocument, MAX_KEYBOXES_PER_FILE, MAX_KEYS_PER_KEYBOX};
use der::{Decode, Encode};
use sha2::{Digest, Sha256};
use std::collections::BTreeMap;
use std::sync::{Mutex, OnceLock};
use x509_cert::Certificate;
use zeroize::Zeroizing;

pub const KEY_ID_BYTES: usize = 16;
pub const MAX_STORED_KEYS: usize = MAX_KEYBOXES_PER_FILE * MAX_KEYS_PER_KEYBOX;
pub type KeyId = [u8; KEY_ID_BYTES];

#[derive(Debug)]
pub struct PublicKeyRecord {
    pub id: KeyId,
    pub algorithm: String,
    pub certificates_der: Vec<Vec<u8>>,
}

struct StoredKey {
    id: KeyId,
    algorithm: SigningAlgorithm,
    algorithm_name: String,
    private_key_pkcs8: Zeroizing<Vec<u8>>,
    certificates_der: Vec<Vec<u8>>,
}

#[derive(Default)]
struct KeyStore {
    keys: BTreeMap<KeyId, StoredKey>,
}

static STORE: OnceLock<Mutex<KeyStore>> = OnceLock::new();

pub fn register_document(document: &KeyboxDocument) -> Result<Vec<PublicKeyRecord>, &'static str> {
    if document.keys.is_empty() || document.keys.len() > MAX_STORED_KEYS {
        return Err("keybox key count exceeds store bound");
    }

    let mut pending = Vec::new();
    pending
        .try_reserve_exact(document.keys.len())
        .map_err(|_| "keybox store allocation failed")?;
    for raw in &document.keys {
        pending.push(build_stored_key(
            &raw.algorithm,
            &raw.private_key_pem,
            &raw.certificates_pem,
        )?);
    }

    let store = STORE.get_or_init(|| Mutex::new(KeyStore::default()));
    let mut guard = store.lock().map_err(|_| "keybox store lock poisoned")?;
    let additional = pending
        .iter()
        .filter(|candidate| !guard.keys.contains_key(&candidate.id))
        .count();
    if guard.keys.len().saturating_add(additional) > MAX_STORED_KEYS {
        return Err("keybox store capacity exceeded");
    }

    for candidate in &pending {
        if let Some(existing) = guard.keys.get(&candidate.id) {
            if !same_key(existing, candidate) {
                return Err("opaque key identifier collision");
            }
        }
    }

    let mut public = Vec::new();
    public
        .try_reserve_exact(pending.len())
        .map_err(|_| "keybox metadata allocation failed")?;
    for key in pending {
        public.push(PublicKeyRecord {
            id: key.id,
            algorithm: key.algorithm_name.clone(),
            certificates_der: key.certificates_der.clone(),
        });
        guard.keys.insert(key.id, key);
    }
    Ok(public)
}

pub fn with_key<T>(
    id: &KeyId,
    operation: impl FnOnce(SigningAlgorithm, &[u8], &[u8]) -> Result<T, &'static str>,
) -> Result<T, &'static str> {
    let store = STORE.get_or_init(|| Mutex::new(KeyStore::default()));
    let guard = store.lock().map_err(|_| "keybox store lock poisoned")?;
    let key = guard
        .keys
        .get(id)
        .ok_or("opaque key identifier is not registered")?;
    let issuer = key
        .certificates_der
        .first()
        .ok_or("registered key has no certificate")?;
    operation(key.algorithm, key.private_key_pkcs8.as_slice(), issuer)
}

pub fn retain_only(ids: &[KeyId]) -> Result<(), &'static str> {
    if ids.len() > MAX_STORED_KEYS {
        return Err("active key set exceeds store bound");
    }
    let store = STORE.get_or_init(|| Mutex::new(KeyStore::default()));
    let mut guard = store.lock().map_err(|_| "keybox store lock poisoned")?;
    if ids.iter().any(|id| !guard.keys.contains_key(id)) {
        return Err("active key set contains an unregistered identifier");
    }
    guard.keys.retain(|id, _| ids.contains(id));
    Ok(())
}

fn build_stored_key(
    algorithm: &str,
    private_key_pem: &str,
    certificates_pem: &[String],
) -> Result<StoredKey, &'static str> {
    if certificates_pem.is_empty() {
        return Err("keybox certificate chain is empty");
    }
    let (signing_algorithm, algorithm_name) = normalize_algorithm(algorithm)?;
    let private_key_pkcs8 = normalize_private_key_pkcs8(algorithm, private_key_pem)
        .map_err(|_| "keybox private key rejected")?;
    let private_spki = public_key_spki_from_pkcs8(algorithm, private_key_pkcs8.as_slice())
        .map_err(|_| "keybox private key rejected")?;

    let mut certificates_der = Vec::new();
    certificates_der
        .try_reserve_exact(certificates_pem.len())
        .map_err(|_| "keybox certificate allocation failed")?;
    for pem in certificates_pem {
        certificates_der.push(decode_certificate_pem(pem)?);
    }

    let leaf = Certificate::from_der(
        certificates_der
            .first()
            .ok_or("keybox certificate chain is empty")?,
    )
    .map_err(|_| "keybox leaf certificate rejected")?;
    let leaf_spki = leaf
        .tbs_certificate
        .subject_public_key_info
        .to_der()
        .map_err(|_| "keybox leaf public key encoding failed")?;
    if private_spki != leaf_spki {
        return Err("keybox private key does not match leaf certificate");
    }

    let id = derive_key_id(
        algorithm_name,
        private_key_pkcs8.as_slice(),
        &certificates_der[0],
    );
    Ok(StoredKey {
        id,
        algorithm: signing_algorithm,
        algorithm_name: algorithm_name.to_string(),
        private_key_pkcs8,
        certificates_der,
    })
}

fn decode_certificate_pem(pem: &str) -> Result<Vec<u8>, &'static str> {
    let trimmed = pem.trim();
    if trimmed.is_empty() {
        return Err("certificate PEM is empty");
    }
    let mut lines = trimmed.lines().map(str::trim);
    if lines.next() != Some("-----BEGIN CERTIFICATE-----") {
        return Err("certificate PEM header is invalid");
    }

    let mut encoded = String::new();
    let mut saw_end = false;
    for line in lines {
        if line == "-----END CERTIFICATE-----" {
            saw_end = true;
            break;
        }
        if line.starts_with("-----") || line.is_empty() {
            return Err("certificate PEM body is invalid");
        }
        encoded
            .try_reserve(line.len())
            .map_err(|_| "certificate PEM allocation failed")?;
        encoded.push_str(line);
        if encoded.len() > MAX_CERTIFICATE_DER_BYTES.saturating_mul(2) {
            return Err("certificate PEM exceeds bound");
        }
    }
    if !saw_end || encoded.is_empty() {
        return Err("certificate PEM footer is missing");
    }

    let der = STANDARD
        .decode(encoded.as_bytes())
        .map_err(|_| "certificate PEM base64 is invalid")?;
    if der.is_empty() || der.len() > MAX_CERTIFICATE_DER_BYTES {
        return Err("certificate DER exceeds bound");
    }
    Certificate::from_der(&der).map_err(|_| "certificate DER rejected")?;
    Ok(der)
}

fn normalize_algorithm(algorithm: &str) -> Result<(SigningAlgorithm, &'static str), &'static str> {
    if algorithm.eq_ignore_ascii_case("EC") || algorithm.eq_ignore_ascii_case("ECDSA") {
        Ok((SigningAlgorithm::EcP256Sha256, "EC"))
    } else if algorithm.eq_ignore_ascii_case("RSA") {
        Ok((SigningAlgorithm::RsaPkcs1Sha256, "RSA"))
    } else {
        Err("unsupported keybox algorithm")
    }
}

fn derive_key_id(algorithm: &str, private_key_pkcs8: &[u8], leaf_der: &[u8]) -> KeyId {
    let mut hash = Sha256::new();
    hash.update(b"CleveresTricky opaque key id v1\0");
    hash.update(algorithm.as_bytes());
    hash.update([0]);
    hash.update(private_key_pkcs8);
    hash.update(leaf_der);
    let digest = hash.finalize();
    let mut id = [0u8; KEY_ID_BYTES];
    id.copy_from_slice(&digest[..KEY_ID_BYTES]);
    id
}

fn same_key(left: &StoredKey, right: &StoredKey) -> bool {
    left.algorithm == right.algorithm
        && left.private_key_pkcs8.as_slice() == right.private_key_pkcs8.as_slice()
        && left.certificates_der == right.certificates_der
}

#[cfg(test)]
mod tests {
    use super::*;
    use cleverestricky_xml_core::parse_keybox_xml_bytes;

    const VALID_EC: &[u8] =
        include_bytes!("../../../service/src/test/resources/keybox/valid_ec.xml");

    #[test]
    fn registration_returns_only_opaque_id_and_certificate_der() {
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let records = register_document(&document).unwrap();
        assert_eq!(records.len(), 1);
        assert_eq!(records[0].algorithm, "EC");
        assert!(records[0].id.iter().any(|byte| *byte != 0));
        assert!(!records[0].certificates_der.is_empty());
        assert!(records[0].certificates_der[0].starts_with(&[0x30]));
        assert!(records[0]
            .certificates_der
            .iter()
            .all(|certificate| !certificate
                .windows(10)
                .any(|window| window == b"-----BEGIN")));
    }

    #[test]
    fn deterministic_identifier_survives_reregistration() {
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let first = register_document(&document).unwrap()[0].id;
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let second = register_document(&document).unwrap()[0].id;
        assert_eq!(first, second);
        with_key(&first, |algorithm, private, issuer| {
            assert_eq!(algorithm, SigningAlgorithm::EcP256Sha256);
            assert!(!private.is_empty());
            assert!(!issuer.is_empty());
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn active_set_prunes_unreferenced_secret_material() {
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let id = register_document(&document).unwrap()[0].id;
        retain_only(&[id]).unwrap();
        assert!(with_key(&id, |_, _, _| Ok(())).is_ok());
        assert!(retain_only(&[[0x55; KEY_ID_BYTES]]).is_err());
    }
}
