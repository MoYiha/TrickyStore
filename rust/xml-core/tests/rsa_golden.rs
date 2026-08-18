// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_xml_core::parse_keybox_xml_bytes;

const VALID_RSA: &[u8] = include_bytes!("../../../service/src/test/resources/keybox/valid_rsa.xml");

#[test]
fn shared_rsa_fixture_matches_structural_oracle() {
    let parsed = parse_keybox_xml_bytes(VALID_RSA).unwrap();
    assert_eq!(parsed.declared_keyboxes, 1);
    assert_eq!(parsed.keybox_count, 1);
    assert_eq!(parsed.keys.len(), 1);
    let key = &parsed.keys[0];
    assert_eq!(key.algorithm, "RSA");
    assert!(key
        .private_key_pem
        .contains("-----BEGIN RSA PRIVATE KEY-----"));
    assert_eq!(key.certificates_pem.len(), 1);
    assert!(key.certificates_pem[0].contains("-----BEGIN CERTIFICATE-----"));
}
