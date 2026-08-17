// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use der::asn1::{Any, AnyRef};
use der::{Decode, Encode, Tag, TagNumber, Tagged};

#[test]
fn android_high_context_tags_round_trip_canonically() {
    for number in [704u32, 724, 900] {
        let inner = Any::new(Tag::OctetString, b"value".to_vec())
            .unwrap()
            .to_der()
            .unwrap();
        let encoded = Any::new(
            Tag::ContextSpecific {
                constructed: true,
                number: TagNumber(number),
            },
            inner.clone(),
        )
        .unwrap()
        .to_der()
        .unwrap();

        let decoded = AnyRef::from_der(&encoded).unwrap();
        assert_eq!(
            decoded.tag(),
            Tag::ContextSpecific {
                constructed: true,
                number: TagNumber(number),
            }
        );
        assert_eq!(decoded.value(), inner.as_slice());
        assert_eq!(decoded.to_der().unwrap(), encoded);
    }
}
