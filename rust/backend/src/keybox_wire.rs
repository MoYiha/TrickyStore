// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_keybox_core::normalize_private_key_pkcs8;
use cleverestricky_xml_core::{
    parse_keybox_xml_bytes, KeyboxDocument, MAX_CERTIFICATES_PER_CHAIN, MAX_DOCUMENT_UTF8_BYTES,
    MAX_KEYBOXES_PER_FILE, MAX_KEYS_PER_KEYBOX,
};
use zeroize::{Zeroize, Zeroizing};

pub const MAX_KEYBOX_XML_BYTES: usize = MAX_DOCUMENT_UTF8_BYTES;
const MAX_TOTAL_KEYS: usize = MAX_KEYBOXES_PER_FILE * MAX_KEYS_PER_KEYBOX;
const MAX_TOTAL_CERTIFICATES: usize = MAX_TOTAL_KEYS * MAX_CERTIFICATES_PER_CHAIN;
const WIRE_VERSION: u8 = 2;
const FIXED_HEADER_BYTES: usize = 5;
const KEY_HEADER_BYTES: usize = 6;
const CERTIFICATE_HEADER_BYTES: usize = 4;
pub const MAX_KEYBOX_WIRE_OVERHEAD_BYTES: usize = FIXED_HEADER_BYTES
    + MAX_TOTAL_KEYS * KEY_HEADER_BYTES
    + MAX_TOTAL_CERTIFICATES * CERTIFICATE_HEADER_BYTES;
pub const MAX_KEYBOX_RESPONSE_BYTES: usize = MAX_KEYBOX_XML_BYTES + MAX_KEYBOX_WIRE_OVERHEAD_BYTES;

struct WireKey<'a> {
    algorithm: &'a str,
    private_key_pkcs8: Zeroizing<Vec<u8>>,
    certificates_pem: &'a [String],
}

pub fn parse_and_encode(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
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

    let keys = normalize_keys(&document)?;
    encode_document(&document, &keys)
}

fn request_size_is_valid(length: usize) -> bool {
    length != 0 && length <= MAX_KEYBOX_XML_BYTES
}

fn normalize_keys(document: &KeyboxDocument) -> Result<Vec<WireKey<'_>>, &'static str> {
    let mut keys = Vec::new();
    keys.try_reserve_exact(document.keys.len())
        .map_err(|_| "keybox normalization allocation failed")?;
    for key in &document.keys {
        let private_key_pkcs8 = normalize_private_key_pkcs8(&key.algorithm, &key.private_key_pem)
            .map_err(|_| "keybox private key rejected")?;
        keys.push(WireKey {
            algorithm: &key.algorithm,
            private_key_pkcs8,
            certificates_pem: &key.certificates_pem,
        });
    }
    Ok(keys)
}

fn encode_document(
    document: &KeyboxDocument,
    keys: &[WireKey<'_>],
) -> Result<Vec<u8>, &'static str> {
    validate_wire_fields(document, keys)?;
    let total = encoded_len(keys)?;
    if total > MAX_KEYBOX_RESPONSE_BYTES {
        return Err("keybox response exceeds wire bound");
    }

    let mut output = Vec::new();
    output
        .try_reserve_exact(total)
        .map_err(|_| "keybox response allocation failed")?;
    output.push(WIRE_VERSION);
    output.push(document.declared_keyboxes as u8);
    output.push(document.keybox_count as u8);
    output.extend_from_slice(&(keys.len() as u16).to_be_bytes());

    for key in keys {
        output.push(key.algorithm.len() as u8);
        output.push(key.certificates_pem.len() as u8);
        output.extend_from_slice(&(key.private_key_pkcs8.len() as u32).to_be_bytes());
        output.extend_from_slice(key.algorithm.as_bytes());
        output.extend_from_slice(key.private_key_pkcs8.as_slice());
        for certificate in key.certificates_pem {
            output.extend_from_slice(&(certificate.len() as u32).to_be_bytes());
            output.extend_from_slice(certificate.as_bytes());
        }
    }
    debug_assert_eq!(output.len(), total);
    Ok(output)
}

fn validate_wire_fields(
    document: &KeyboxDocument,
    keys: &[WireKey<'_>],
) -> Result<(), &'static str> {
    if document.declared_keyboxes > u8::MAX as usize {
        return Err("keybox declaration exceeds wire bound");
    }
    if document.keybox_count > u8::MAX as usize {
        return Err("keybox count exceeds wire bound");
    }
    if keys.len() != document.keys.len() || keys.len() > u16::MAX as usize {
        return Err("key count exceeds wire bound");
    }
    for key in keys {
        if key.algorithm.len() > u8::MAX as usize {
            return Err("key algorithm exceeds wire bound");
        }
        if key.certificates_pem.len() > u8::MAX as usize {
            return Err("certificate count exceeds wire bound");
        }
        if key.private_key_pkcs8.len() > u32::MAX as usize {
            return Err("private key exceeds wire bound");
        }
        if key
            .certificates_pem
            .iter()
            .any(|certificate| certificate.len() > u32::MAX as usize)
        {
            return Err("certificate exceeds wire bound");
        }
    }
    Ok(())
}

fn encoded_len(keys: &[WireKey<'_>]) -> Result<usize, &'static str> {
    let mut total = FIXED_HEADER_BYTES;
    for key in keys {
        total = total
            .checked_add(KEY_HEADER_BYTES)
            .and_then(|value| value.checked_add(key.algorithm.len()))
            .and_then(|value| value.checked_add(key.private_key_pkcs8.len()))
            .ok_or("keybox response size overflow")?;
        for certificate in key.certificates_pem {
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
    fn shared_ec_fixture_encodes_pkcs8_length_delimited_response() {
        let response = parse_and_encode(VALID_EC.to_vec()).unwrap();
        assert_eq!(response[0], WIRE_VERSION);
        assert_eq!(response[1], 1);
        assert_eq!(response[2], 1);
        assert_eq!(u16::from_be_bytes(response[3..5].try_into().unwrap()), 1);

        let mut offset = FIXED_HEADER_BYTES;
        let algorithm_len = response[offset] as usize;
        let certificate_count = response[offset + 1] as usize;
        let private_key_len =
            u32::from_be_bytes(response[offset + 2..offset + 6].try_into().unwrap()) as usize;
        offset += KEY_HEADER_BYTES;
        assert_eq!(&response[offset..offset + algorithm_len], b"ecdsa");
        offset += algorithm_len;
        let private_key = &response[offset..offset + private_key_len];
        assert_eq!(private_key.first().copied(), Some(0x30));
        assert!(!private_key
            .windows(10)
            .any(|window| window == b"-----BEGIN"));
        offset += private_key_len;
        assert_eq!(certificate_count, 1);
        let certificate_len =
            u32::from_be_bytes(response[offset..offset + 4].try_into().unwrap()) as usize;
        offset += CERTIFICATE_HEADER_BYTES;
        assert!(
            std::str::from_utf8(&response[offset..offset + certificate_len])
                .unwrap()
                .starts_with("-----BEGIN CERTIFICATE-----")
        );
        offset += certificate_len;
        assert_eq!(offset, response.len());
        assert!(response.len() <= MAX_KEYBOX_RESPONSE_BYTES);
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
