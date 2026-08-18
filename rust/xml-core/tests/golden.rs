use cleverestricky_xml_core::parse_keybox_xml;

const VALID_EC: &str = include_str!("../../../service/src/test/resources/keybox/valid_ec.xml");

#[test]
fn shared_ec_fixture_matches_structural_oracle() {
    let parsed = parse_keybox_xml(VALID_EC).expect("shared EC keybox fixture must parse");
    assert_eq!(parsed.declared_keyboxes, 1);
    assert_eq!(parsed.keybox_count, 1);
    assert_eq!(parsed.keys.len(), 1);

    let key = &parsed.keys[0];
    assert_eq!(key.algorithm, "ecdsa");
    assert!(key
        .private_key_pem
        .starts_with("-----BEGIN EC PRIVATE KEY-----"));
    assert!(key
        .private_key_pem
        .ends_with("-----END EC PRIVATE KEY-----"));
    assert!(key
        .private_key_pem
        .contains("MHcCAQEEIB0ruYIH/2OWTKh/ISJ40MzTNAU/9oSgM2ib5Iq+PyGA"));
    assert_eq!(key.certificates_pem.len(), 1);
    assert!(key.certificates_pem[0].starts_with("-----BEGIN CERTIFICATE-----"));
    assert!(key.certificates_pem[0].ends_with("-----END CERTIFICATE-----"));
}
