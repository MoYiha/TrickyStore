// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_attestation_core::{rewrite_extension, PatchLevels, RewriteRequest};
use der::asn1::Any;
use der::{Encode, Tag};

#[test]
fn rejects_zero_verified_boot_material() {
    let extension = key_description();
    let zero = [0u8; 32];
    let nonzero = [0x42u8; 32];

    for (boot_key, boot_hash) in [(&zero, &nonzero), (&nonzero, &zero)] {
        let result = rewrite_extension(&RewriteRequest {
            extension_der: &extension,
            patch_levels: PatchLevels::default(),
            id_overrides: &[],
            module_hash: None,
            verified_boot_key: boot_key,
            verified_boot_hash: boot_hash,
        });
        assert!(
            result.is_err(),
            "zero verified boot material must fail closed",
        );
    }
}

fn key_description() -> Vec<u8> {
    sequence([
        400i32.to_der().unwrap(),
        Any::new(Tag::Enumerated, vec![1])
            .unwrap()
            .to_der()
            .unwrap(),
        400i32.to_der().unwrap(),
        Any::new(Tag::Enumerated, vec![1])
            .unwrap()
            .to_der()
            .unwrap(),
        Any::new(Tag::OctetString, Vec::<u8>::new())
            .unwrap()
            .to_der()
            .unwrap(),
        Any::new(Tag::OctetString, Vec::<u8>::new())
            .unwrap()
            .to_der()
            .unwrap(),
        sequence([]),
        sequence([]),
    ])
}

fn sequence<const N: usize>(fields: [Vec<u8>; N]) -> Vec<u8> {
    let mut value = Vec::new();
    for field in fields {
        value.extend_from_slice(&field);
    }
    Any::new(Tag::Sequence, value).unwrap().to_der().unwrap()
}
