// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#[path = "../src/keybox_wire.rs"]
mod keybox_wire;

const VALID_RSA: &[u8] = include_bytes!("../../../service/src/test/resources/keybox/valid_rsa.xml");

#[test]
fn shared_rsa_fixture_uses_der_private_key_wire() {
    let response = keybox_wire::parse_and_encode(VALID_RSA.to_vec()).unwrap();
    assert_eq!(response[0], 2);
    assert_eq!(response[1], 1);
    assert_eq!(response[2], 1);
    assert_eq!(u16::from_be_bytes(response[3..5].try_into().unwrap()), 1);

    let algorithm_len = response[5] as usize;
    let certificate_count = response[6] as usize;
    let private_key_len = u32::from_be_bytes(response[7..11].try_into().unwrap()) as usize;
    let algorithm_start = 11;
    let private_key_start = algorithm_start + algorithm_len;
    let private_key_end = private_key_start + private_key_len;

    assert_eq!(&response[algorithm_start..private_key_start], b"RSA");
    assert_eq!(certificate_count, 1);
    let private_key = &response[private_key_start..private_key_end];
    assert_eq!(private_key.first().copied(), Some(0x30));
    assert!(!private_key
        .windows(10)
        .any(|window| window == b"-----BEGIN"));
}
