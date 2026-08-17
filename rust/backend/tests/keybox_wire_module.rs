#[path = "../src/keybox_wire.rs"]
mod keybox_wire;

#[test]
fn shared_fixture_compiles_through_backend_wire_primitive() {
    let xml = include_bytes!("../../../service/src/test/resources/keybox/valid_ec.xml");
    let response = keybox_wire::parse_and_encode(xml.to_vec())
        .expect("shared EC fixture must encode through the backend primitive");
    assert!(!response.is_empty());
    assert!(response.len() <= keybox_wire::MAX_KEYBOX_RESPONSE_BYTES);
}
