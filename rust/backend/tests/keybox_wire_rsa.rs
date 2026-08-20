// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#[path = "../src/keybox_wire.rs"]
mod keybox_wire;

const VALID_RSA: &[u8] = include_bytes!("../../../service/src/test/resources/keybox/valid_rsa.xml");

#[test]
fn shared_rsa_fixture_uses_opaque_id_and_certificate_der_wire() {
    let response = keybox_wire::parse_and_encode(VALID_RSA.to_vec()).unwrap();
    assert_eq!(response[0], keybox_wire::WIRE_VERSION);
    assert_eq!(response[1], 1);
    assert_eq!(response[2], 1);
    assert_eq!(u16::from_be_bytes(response[3..5].try_into().unwrap()), 1);

    let algorithm_len = response[keybox_wire::FIXED_HEADER_BYTES] as usize;
    let certificate_count = response[keybox_wire::FIXED_HEADER_BYTES + 1] as usize;
    let id_start = keybox_wire::FIXED_HEADER_BYTES + 2;
    let id_end = id_start + 16;
    let algorithm_start = id_end;
    let algorithm_end = algorithm_start + algorithm_len;
    assert!(response[id_start..id_end].iter().any(|byte| *byte != 0));
    assert_eq!(&response[algorithm_start..algorithm_end], b"RSA");
    assert_eq!(certificate_count, 1);

    let certificate_len = u32::from_be_bytes(
        response[algorithm_end..algorithm_end + 4]
            .try_into()
            .unwrap(),
    ) as usize;
    let certificate_start = algorithm_end + 4;
    let certificate_end = certificate_start + certificate_len;
    assert_eq!(response[certificate_start], 0x30);
    assert_eq!(certificate_end, response.len());
    assert!(!response
        .windows(16)
        .any(|window| window == b"PRIVATE KEY-----"));
}
