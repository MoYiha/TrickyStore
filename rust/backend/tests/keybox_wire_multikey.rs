// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#[path = "../src/keybox_wire.rs"]
mod keybox_wire;

const VALID_EC: &str = include_str!("../../../service/src/test/resources/keybox/valid_ec.xml");
const VALID_RSA: &str = include_str!("../../../service/src/test/resources/keybox/valid_rsa.xml");

#[test]
fn one_xml_keybox_with_ec_and_rsa_keys_flattens_to_two_wire_keys() {
    let xml = format!(
        "<AndroidAttestation><NumberOfKeyboxes>1</NumberOfKeyboxes><Keybox>{}{}</Keybox></AndroidAttestation>",
        key_element(VALID_EC),
        key_element(VALID_RSA)
    );
    let response = keybox_wire::parse_and_encode(xml.into_bytes()).unwrap();

    assert_eq!(response[0], 2);
    assert_eq!(response[1], 1);
    assert_eq!(response[2], 1);
    assert_eq!(u16::from_be_bytes(response[3..5].try_into().unwrap()), 2);

    let mut offset = 5usize;
    let mut algorithms = Vec::new();
    for _ in 0..2 {
        let algorithm_len = response[offset] as usize;
        let certificate_count = response[offset + 1] as usize;
        let private_key_len = read_u32(&response, offset + 2);
        offset += 6;
        let algorithm_end = offset + algorithm_len;
        algorithms.push(std::str::from_utf8(&response[offset..algorithm_end]).unwrap());
        offset = algorithm_end + private_key_len;
        for _ in 0..certificate_count {
            let certificate_len = read_u32(&response, offset);
            offset += 4 + certificate_len;
        }
    }
    assert_eq!(algorithms, ["ecdsa", "RSA"]);
    assert_eq!(offset, response.len());
}

fn key_element(xml: &str) -> &str {
    let start = xml.find("<Key ").unwrap();
    let relative_end = xml[start..].find("</Key>").unwrap();
    &xml[start..start + relative_end + "</Key>".len()]
}

fn read_u32(input: &[u8], offset: usize) -> usize {
    u32::from_be_bytes(input[offset..offset + 4].try_into().unwrap()) as usize
}
