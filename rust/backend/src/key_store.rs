// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use base64::engine::general_purpose::STANDARD;
use base64::Engine as _;
use cleverestricky_certificate_core::{SigningAlgorithm, MAX_CERTIFICATE_DER_BYTES};
use cleverestricky_keybox_core::{normalize_private_key_pkcs8, public_key_spki_from_pkcs8};
use cleverestricky_xml_core::{KeyboxDocument, MAX_KEYBOXES_PER_FILE, MAX_KEYS_PER_KEYBOX};
use der::{Decode, Encode};
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, BTreeSet, VecDeque};
use std::sync::{Arc, Mutex, OnceLock};
use x509_cert::Certificate;
use zeroize::Zeroizing;

pub const KEY_ID_BYTES: usize = 16;
pub const MAX_STORED_KEYS: usize = MAX_KEYBOXES_PER_FILE * MAX_KEYS_PER_KEYBOX;
const MAX_STAGED_KEYS: usize = MAX_STORED_KEYS * 2;
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
    keys: BTreeMap<KeyId, Arc<StoredKey>>,
    active_ids: BTreeSet<KeyId>,
    transient_order: VecDeque<KeyId>,
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
    for candidate in &pending {
        if let Some(existing) = guard.keys.get(&candidate.id) {
            if !same_key(existing.as_ref(), candidate) {
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
        if guard.keys.contains_key(&key.id) {
            touch_transient(&mut guard, key.id);
            continue;
        }
        make_room_for_transient(&mut guard)?;
        let id = key.id;
        guard.keys.insert(id, Arc::new(key));
        guard.transient_order.push_back(id);
    }
    Ok(public)
}

pub fn with_key<T>(
    id: &KeyId,
    operation: impl FnOnce(SigningAlgorithm, &[u8], &[u8]) -> Result<T, &'static str>,
) -> Result<T, &'static str> {
    let key = {
        let store = STORE.get_or_init(|| Mutex::new(KeyStore::default()));
        let guard = store.lock().map_err(|_| "keybox store lock poisoned")?;
        if !guard.active_ids.contains(id) {
            return Err("opaque key identifier is not active");
        }
        Arc::clone(
            guard
                .keys
                .get(id)
                .ok_or("opaque key identifier is not registered")?,
        )
    };
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
    let active: BTreeSet<KeyId> = ids.iter().copied().collect();
    if active.len() != ids.len() || active.iter().any(|id| !guard.keys.contains_key(id)) {
        return Err("active key set contains an unregistered identifier");
    }
    guard.keys.retain(|id, _| active.contains(id));
    guard.active_ids = active;
    guard.transient_order.clear();
    Ok(())
}

fn make_room_for_transient(store: &mut KeyStore) -> Result<(), &'static str> {
    while store.keys.len() >= MAX_STAGED_KEYS {
        let Some(candidate) = store.transient_order.pop_front() else {
            return Err("keybox store capacity exceeded");
        };
        if store.active_ids.contains(&candidate) {
            continue;
        }
        store.keys.remove(&candidate);
    }
    Ok(())
}

fn touch_transient(store: &mut KeyStore, id: KeyId) {
    if store.active_ids.contains(&id) {
        return;
    }
    store.transient_order.retain(|candidate| candidate != &id);
    store.transient_order.push_back(id);
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
    use std::sync::mpsc;
    use std::time::Duration;

    const VALID_EC: &[u8] =
        include_bytes!("../../../service/src/test/resources/keybox/valid_ec.xml");
    static TEST_LOCK: Mutex<()> = Mutex::new(());

    fn reset_store() {
        let store = STORE.get_or_init(|| Mutex::new(KeyStore::default()));
        *store.lock().unwrap() = KeyStore::default();
    }

    fn synthetic_key(id: KeyId) -> StoredKey {
        StoredKey {
            id,
            algorithm: SigningAlgorithm::EcP256Sha256,
            algorithm_name: "EC".to_string(),
            private_key_pkcs8: Zeroizing::new(vec![id[0].max(1)]),
            certificates_der: vec![vec![0x30, id[0]]],
        }
    }

    fn insert_transient(id: KeyId) {
        let store = STORE.get_or_init(|| Mutex::new(KeyStore::default()));
        let mut guard = store.lock().unwrap();
        make_room_for_transient(&mut guard).unwrap();
        guard.keys.insert(id, Arc::new(synthetic_key(id)));
        guard.transient_order.push_back(id);
    }

    #[test]
    fn registration_returns_only_opaque_id_and_certificate_der() {
        let _serial = TEST_LOCK.lock().unwrap();
        reset_store();
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
    fn deterministic_identifier_requires_activation_before_signing() {
        let _serial = TEST_LOCK.lock().unwrap();
        reset_store();
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let first = register_document(&document).unwrap()[0].id;
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let second = register_document(&document).unwrap()[0].id;
        assert_eq!(first, second);
        assert!(with_key(&first, |_, _, _| Ok(())).is_err());
        retain_only(&[first]).unwrap();
        with_key(&first, |algorithm, private, issuer| {
            assert_eq!(algorithm, SigningAlgorithm::EcP256Sha256);
            assert!(!private.is_empty());
            assert!(!issuer.is_empty());
            Ok(())
        })
        .unwrap();
    }

    #[test]
    fn replaced_deleted_and_empty_active_sets_prune_old_secrets() {
        let _serial = TEST_LOCK.lock().unwrap();
        reset_store();
        let first = [1; KEY_ID_BYTES];
        let second = [2; KEY_ID_BYTES];
        insert_transient(first);
        insert_transient(second);
        retain_only(&[first]).unwrap();
        assert!(with_key(&first, |_, _, _| Ok(())).is_ok());
        assert!(with_key(&second, |_, _, _| Ok(())).is_err());

        let replacement = [3; KEY_ID_BYTES];
        insert_transient(replacement);
        retain_only(&[replacement]).unwrap();
        assert!(with_key(&first, |_, _, _| Ok(())).is_err());
        assert!(with_key(&replacement, |_, _, _| Ok(())).is_ok());

        retain_only(&[]).unwrap();
        assert!(with_key(&replacement, |_, _, _| Ok(())).is_err());
    }

    #[test]
    fn rejected_transient_flood_cannot_exhaust_future_legitimate_activation() {
        let _serial = TEST_LOCK.lock().unwrap();
        reset_store();
        let active = [1; KEY_ID_BYTES];
        insert_transient(active);
        retain_only(&[active]).unwrap();

        for index in 0..(MAX_STAGED_KEYS + MAX_STORED_KEYS + 32) {
            let mut id = [0u8; KEY_ID_BYTES];
            id[0] = 2;
            id[8..].copy_from_slice(&(index as u64).to_be_bytes());
            if id == active {
                continue;
            }
            insert_transient(id);
        }

        let future = [0xee; KEY_ID_BYTES];
        insert_transient(future);
        {
            let store = STORE.get_or_init(|| Mutex::new(KeyStore::default()));
            let guard = store.lock().unwrap();
            assert!(guard.keys.len() <= MAX_STAGED_KEYS);
            assert!(guard.keys.contains_key(&active));
            assert!(guard.keys.contains_key(&future));
        }
        retain_only(&[future]).unwrap();
        assert!(with_key(&active, |_, _, _| Ok(())).is_err());
        assert!(with_key(&future, |_, _, _| Ok(())).is_ok());
    }

    #[test]
    fn long_crypto_operation_does_not_hold_store_mutex() {
        let _serial = TEST_LOCK.lock().unwrap();
        reset_store();
        let document = parse_keybox_xml_bytes(VALID_EC).unwrap();
        let id = register_document(&document).unwrap()[0].id;
        retain_only(&[id]).unwrap();

        let (crypto_started_tx, crypto_started_rx) = mpsc::channel();
        let (release_crypto_tx, release_crypto_rx) = mpsc::channel();
        let crypto = std::thread::spawn(move || {
            with_key(&id, |_, private, issuer| {
                assert!(!private.is_empty());
                assert!(!issuer.is_empty());
                crypto_started_tx.send(()).unwrap();
                release_crypto_rx.recv().unwrap();
                Ok(())
            })
            .unwrap();
        });
        crypto_started_rx.recv_timeout(Duration::from_secs(1)).unwrap();

        let (control_done_tx, control_done_rx) = mpsc::channel();
        let control = std::thread::spawn(move || {
            let result = retain_only(&[id]);
            control_done_tx.send(result).unwrap();
        });
        assert_eq!(
            control_done_rx.recv_timeout(Duration::from_millis(250)).unwrap(),
            Ok(())
        );

        release_crypto_tx.send(()).unwrap();
        crypto.join().unwrap();
        control.join().unwrap();
    }
}
