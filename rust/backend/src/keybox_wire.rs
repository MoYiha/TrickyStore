// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#[path = "key_store.rs"]
pub(crate) mod key_store;

use cleverestricky_xml_core::{
    parse_keybox_xml_bytes, MAX_CERTIFICATES_PER_CHAIN, MAX_DOCUMENT_UTF8_BYTES,
    MAX_KEYBOXES_PER_FILE, MAX_KEYS_PER_KEYBOX,
};
use key_store::{KeyId, PublicKeyRecord, KEY_ID_BYTES, MAX_STORED_KEYS};
use zeroize::Zeroize;

pub const MAX_KEYBOX_XML_BYTES: usize = MAX_DOCUMENT_UTF8_BYTES;
const MAX_TOTAL_KEYS: usize = MAX_KEYBOXES_PER_FILE * MAX_KEYS_PER_KEYBOX;
const MAX_TOTAL_CERTIFICATES: usize = MAX_TOTAL_KEYS * MAX_CERTIFICATES_PER_CHAIN;
const WIRE_VERSION: u8 = 3;
const FIXED_HEADER_BYTES: usize = 5;
const KEY_HEADER_BYTES: usize = 2 + KEY_ID_BYTES;
const CERTIFICATE_HEADER_BYTES: usize = 4;
const STORE_CONTROL_MAGIC: &[u8; 4] = b"CTKS";
const STORE_CONTROL_VERSION: u8 = 1;
const STORE_ACTION_RETAIN: u8 = 1;
const STORE_CONTROL_HEADER_BYTES: usize = 8;
pub const MAX_KEYBOX_WIRE_OVERHEAD_BYTES: usize = FIXED_HEADER_BYTES
    + MAX_TOTAL_KEYS * KEY_HEADER_BYTES
    + MAX_TOTAL_CERTIFICATES * CERTIFICATE_HEADER_BYTES;
pub const MAX_KEYBOX_RESPONSE_BYTES: usize = MAX_KEYBOX_XML_BYTES + MAX_KEYBOX_WIRE_OVERHEAD_BYTES;

pub fn parse_and_encode(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
    if request.starts_with(STORE_CONTROL_MAGIC) {
        return handle_store_control(request);
    }
    if !request_size_is_valid(request.len()) {
        request.zeroize();
        return Err("keybox XML exceeds operation bound");
    }

    let document = match parse_keybox_xml_bytes(&request) {
        Ok(document) => document,
        Err(_) => {
            request.zeroize();
            return Err("keybox XML rejected");
        }
    };
    request.zeroize();
    drop(request);

    let keys = key_store::register_document(&document)?;
    encode_document(document.declared_keyboxes, document.keybox_count, &keys)
}

fn handle_store_control(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
    let result = (|| {
        if request.len() < STORE_CONTROL_HEADER_BYTES
            || request[0..4] != STORE_CONTROL_MAGIC[..]
            || request[4] != STORE_CONTROL_VERSION
            || request[5] != STORE_ACTION_RETAIN
        {
            return Err("invalid key store control request");
        }
        let count = u16::from_be_bytes([request[6], request[7]]) as usize;
        if count > MAX_STORED_KEYS {
            return Err("active key set exceeds store bound");
        }
        let expected = STORE_CONTROL_HEADER_BYTES
            .checked_add(
                count
                    .checked_mul(KEY_ID_BYTES)
                    .ok_or("active key set size overflow")?,
            )
            .ok_or("active key set size overflow")?;
        if request.len() != expected {
            return Err("invalid active key set length");
        }
        let mut ids = Vec::<KeyId>::new();
        ids.try_reserve_exact(count)
            .map_err(|_| "active key set allocation failed")?;
        for chunk in request[STORE_CONTROL_HEADER_BYTES..].chunks_exact(KEY_ID_BYTES) {
            let id: KeyId = chunk
                .try_into()
                .map_err(|_| "invalid opaque key identifier")?;
            if id.iter().all(|byte| *byte == 0) || ids.contains(&id) {
                return Err("invalid active key identifier set");
            }
            ids.push(id);
        }
        key_store::retain_only(&ids)?;
        Ok(b"ok".to_vec())
    })();
    request.zeroize();
    result
}

fn request_size_is_valid(length: usize) -> bool {
    length != 0 && length <= MAX_KEYBOX_XML_BYTES
}

fn encode_document(
    declared_keyboxes: usize,
    keybox_count: usize,
    keys: &[PublicKeyRecord],
) -> Result<Vec<u8>, &'static str> {
    validate_wire_fields(declared_keyboxes, keybox_count, keys)?;
    let total = encoded_len(keys)?;
    if total > MAX_KEYBOX_RESPONSE_BYTES {
        return Err("keybox response exceeds wire bound");
    }

    let mut output = Vec::new();
    output
        .try_reserve_exact(total)
        .map_err(|_| "keybox response allocation failed")?;
    output.push(WIRE_VERSION);
    output.push(declared_keyboxes as u8);
    output.push(keybox_count as u8);
    output.extend_from_slice(&(keys.len() as u16).to_be_bytes());

    for key in keys {
        output.push(key.algorithm.len() as u8);
        output.push(key.certificates_der.len() as u8);
        output.extend_from_slice(&key.id);
        output.extend_from_slice(key.algorithm.as_bytes());
        for certificate in &key.certificates_der {
            output.extend_from_slice(&(certificate.len() as u32).to_be_bytes());
            output.extend_from_slice(certificate);
        }
    }
    debug_assert_eq!(output.len(), total);
    Ok(output)
}

fn validate_wire_fields(
    declared_keyboxes: usize,
    keybox_count: usize,
    keys: &[PublicKeyRecord],
) -> Result<(), &'static str> {
    if declared_keyboxes == 0 || declared_keyboxes > u8::MAX as usize {
        return Err("keybox declaration exceeds wire bound");
    }
    if keybox_count == 0 || keybox_count > u8::MAX as usize || keybox_count != declared_keyboxes {
        return Err("keybox count exceeds wire bound");
    }
    if keys.len() < keybox_count || keys.len() > u16::MAX as usize {
        return Err("key count exceeds wire bound");
    }
    for key in keys {
        if key.algorithm.is_empty() || key.algorithm.len() > u8::MAX as usize {
            return Err("key algorithm exceeds wire bound");
        }
        if key.id.iter().all(|byte| *byte == 0) {
            return Err("opaque key identifier is invalid");
        }
        if key.certificates_der.is_empty() || key.certificates_der.len() > u8::MAX as usize {
            return Err("certificate count exceeds wire bound");
        }
        if key
            .certificates_der
            .iter()
            .any(|certificate| certificate.is_empty() || certificate.len() > u32::MAX as usize)
        {
            return Err("certificate exceeds wire bound");
        }
    }
    Ok(())
}

fn encoded_len(keys: &[PublicKeyRecord]) -> Result<usize, &'static str> {
    let mut total = FIXED_HEADER_BYTES;
    for key in keys {
        total = total
            .checked_add(KEY_HEADER_BYTES)
            .and_then(|value| value.checked_add(key.algorithm.len()))
            .ok_or("keybox response size overflow")?;
        for certificate in &key.certificates_der {
            total = total
                .checked_add(CERTIFICATE_HEADER_BYTES)
                .and_then(|value| value.checked_add(certificate.len()))
                .ok_or("keybox response size overflow")?;
        }
    }
    Ok(total)
}

#[cfg(test)]
mod tests {
    use super::*;

    const VALID_EC: &[u8] =
        include_bytes!("../../../service/src/test/resources/keybox/valid_ec.xml");

    #[test]
    fn shared_ec_fixture_encodes_opaque_id_and_certificate_der_only() {
        let response = parse_and_encode(VALID_EC.to_vec()).unwrap();
        assert_eq!(response[0], WIRE_VERSION);
        assert_eq!(response[1], 1);
        assert_eq!(response[2], 1);
        assert_eq!(u16::from_be_bytes(response[3..5].try_into().unwrap()), 1);

        let mut offset = FIXED_HEADER_BYTES;
        let algorithm_len = response[offset] as usize;
        let certificate_count = response[offset + 1] as usize;
        let id_start = offset + 2;
        let id_end = id_start + KEY_ID_BYTES;
        assert!(response[id_start..id_end].iter().any(|byte| *byte != 0));
        offset = id_end;
        assert_eq!(&response[offset..offset + algorithm_len], b"EC");
        offset += algorithm_len;
        assert_eq!(certificate_count, 1);
        let certificate_len =
            u32::from_be_bytes(response[offset..offset + 4].try_into().unwrap()) as usize;
        offset += CERTIFICATE_HEADER_BYTES;
        let certificate = &response[offset..offset + certificate_len];
        assert_eq!(certificate.first().copied(), Some(0x30));
        assert!(!certificate
            .windows(10)
            .any(|window| window == b"-----BEGIN"));
        assert!(!response
            .windows(16)
            .any(|window| window == b"PRIVATE KEY-----"));
        offset += certificate_len;
        assert_eq!(offset, response.len());
        assert!(response.len() <= MAX_KEYBOX_RESPONSE_BYTES);
    }

    #[test]
    fn active_set_control_prunes_and_rejects_unknown_handles() {
        let response = parse_and_encode(VALID_EC.to_vec()).unwrap();
        let id_start = FIXED_HEADER_BYTES + 2;
        let id: KeyId = response[id_start..id_start + KEY_ID_BYTES]
            .try_into()
            .unwrap();
        let mut control = Vec::new();
        control.extend_from_slice(STORE_CONTROL_MAGIC);
        control.push(STORE_CONTROL_VERSION);
        control.push(STORE_ACTION_RETAIN);
        control.extend_from_slice(&1u16.to_be_bytes());
        control.extend_from_slice(&id);
        assert_eq!(parse_and_encode(control).unwrap(), b"ok");

        let mut unknown = Vec::new();
        unknown.extend_from_slice(STORE_CONTROL_MAGIC);
        unknown.push(STORE_CONTROL_VERSION);
        unknown.push(STORE_ACTION_RETAIN);
        unknown.extend_from_slice(&1u16.to_be_bytes());
        unknown.extend_from_slice(&[0xa5; KEY_ID_BYTES]);
        assert!(parse_and_encode(unknown).is_err());
    }

    #[test]
    fn malformed_xml_is_rejected_without_response() {
        assert_eq!(
            parse_and_encode(b"<AndroidAttestation>".to_vec()).unwrap_err(),
            "keybox XML rejected"
        );
    }

    #[test]
    fn malformed_private_key_is_rejected_before_response() {
        let invalid = String::from_utf8(VALID_EC.to_vec())
            .unwrap()
            .replace("MHcCAQEE", "NOT-A-KEY-");
        assert_eq!(
            parse_and_encode(invalid.into_bytes()).unwrap_err(),
            "keybox private key rejected"
        );
    }

    #[test]
    fn request_bound_is_checked_without_large_test_allocation() {
        assert!(!request_size_is_valid(0));
        assert!(request_size_is_valid(MAX_KEYBOX_XML_BYTES));
        assert!(!request_size_is_valid(MAX_KEYBOX_XML_BYTES + 1));
    }
}
