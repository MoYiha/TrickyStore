// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_keybox_core::normalize_private_key_pkcs8;
use cleverestricky_xml_core::parse_keybox_xml_bytes;
use rsa::pkcs8::DecodePrivateKey;
use rsa::RsaPrivateKey;

const VALID_RSA: &[u8] = include_bytes!("../../../service/src/test/resources/keybox/valid_rsa.xml");

#[test]
fn shared_rsa_fixture_normalizes_pkcs1_pem_to_pkcs8_der() {
    let document = parse_keybox_xml_bytes(VALID_RSA).unwrap();
    let key = &document.keys[0];
    let normalized = normalize_private_key_pkcs8(&key.algorithm, &key.private_key_pem).unwrap();
    assert!(!normalized.is_empty());
    RsaPrivateKey::from_pkcs8_der(normalized.as_slice()).unwrap();
}
