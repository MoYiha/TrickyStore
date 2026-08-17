// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_attestation_core::{
    rewrite_extension, AttestationIdOverride, PatchLevels, RewriteRequest,
};
use der::asn1::{Any, AnyRef};
use der::{Decode, Encode, Tag, TagNumber, Tagged};

const ROOT_OF_TRUST_TAG: u32 = 704;
const SYSTEM_PATCH_TAG: u32 = 706;
const BRAND_TAG: u32 = 710;
const VENDOR_PATCH_TAG: u32 = 718;
const BOOT_PATCH_TAG: u32 = 719;
const MODULE_HASH_TAG: u32 = 724;
const BOOT_KEY: [u8; 32] = [0x41; 32];
const BOOT_HASH: [u8; 32] = [0x52; 32];

#[test]
fn replaces_present_brand_and_keeps_authorization_tags_sorted() {
    let tee = auth_list([
        explicit_tag(ROOT_OF_TRUST_TAG, &root_of_trust([0; 32], [0; 32])),
        explicit_integer(SYSTEM_PATCH_TAG, 202401),
        explicit_octet(BRAND_TAG, b"OriginalBrand"),
        explicit_integer(VENDOR_PATCH_TAG, 20240205),
        explicit_integer(BOOT_PATCH_TAG, 20240305),
    ]);
    let extension = key_description(100, 100, auth_list([]), tee);
    let ids = [AttestationIdOverride {
        tag: BRAND_TAG,
        value: b"Google",
    }];

    let rewritten = rewrite(&extension, &ids, None);
    let tee_fields = authorization_fields(&rewritten, 7);
    let tags = tee_fields.iter().map(|(tag, _)| *tag).collect::<Vec<_>>();
    assert_eq!(
        tags,
        vec![
            ROOT_OF_TRUST_TAG,
            SYSTEM_PATCH_TAG,
            BRAND_TAG,
            VENDOR_PATCH_TAG,
            BOOT_PATCH_TAG,
        ],
    );
    assert_eq!(
        decode_explicit_octets(
            tee_fields
                .iter()
                .find(|(tag, _)| *tag == BRAND_TAG)
                .expect("brand tag")
                .1
                .as_slice(),
        ),
        b"Google",
    );
}

#[test]
fn inserts_supported_module_hash_only_in_software_list() {
    let tee = auth_list([explicit_tag(
        ROOT_OF_TRUST_TAG,
        &root_of_trust([0; 32], [0; 32]),
    )]);
    let extension = key_description(400, 400, auth_list([]), tee);

    let rewritten = rewrite(&extension, &[], Some(&[0xde, 0xad, 0xbe, 0xef]));
    let software = authorization_fields(&rewritten, 6);
    let tee = authorization_fields(&rewritten, 7);

    let module = software
        .iter()
        .find(|(tag, _)| *tag == MODULE_HASH_TAG)
        .expect("module hash tag");
    assert_eq!(
        decode_explicit_octets(module.1.as_slice()),
        &[0xde, 0xad, 0xbe, 0xef],
    );
    assert!(tee.iter().all(|(tag, _)| *tag != MODULE_HASH_TAG));
}

fn rewrite(
    extension: &[u8],
    ids: &[AttestationIdOverride<'_>],
    module_hash: Option<&[u8]>,
) -> Vec<u8> {
    rewrite_extension(&RewriteRequest {
        extension_der: extension,
        patch_levels: PatchLevels::default(),
        id_overrides: ids,
        module_hash,
        verified_boot_key: &BOOT_KEY,
        verified_boot_hash: &BOOT_HASH,
    })
    .expect("rewrite must succeed")
    .extension_der
}

fn key_description(
    attestation_version: i32,
    keymint_version: i32,
    software: Vec<u8>,
    tee: Vec<u8>,
) -> Vec<u8> {
    sequence([
        attestation_version.to_der().unwrap(),
        Any::new(Tag::Enumerated, vec![1])
            .unwrap()
            .to_der()
            .unwrap(),
        keymint_version.to_der().unwrap(),
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
        software,
        tee,
    ])
}

fn authorization_fields(extension: &[u8], list_index: usize) -> Vec<(u32, Vec<u8>)> {
    let outer = Any::from_der(extension).unwrap();
    let fields = split(outer.value()).unwrap();
    let list = Any::from_der(&fields[list_index]).unwrap();
    split(list.value())
        .unwrap()
        .into_iter()
        .map(|encoded| {
            let any = Any::from_der(&encoded).unwrap();
            let tag = match any.tag() {
                Tag::ContextSpecific {
                    constructed: true,
                    number,
                } => number.value(),
                other => panic!("unexpected authorization tag {other:?}"),
            };
            (tag, encoded)
        })
        .collect()
}

fn decode_explicit_octets(encoded: &[u8]) -> Vec<u8> {
    let outer = Any::from_der(encoded).unwrap();
    let inner = Any::from_der(outer.value()).unwrap();
    assert_eq!(inner.tag(), Tag::OctetString);
    inner.value().to_vec()
}

fn auth_list<const N: usize>(fields: [Vec<u8>; N]) -> Vec<u8> {
    sequence(fields)
}

fn sequence<const N: usize>(fields: [Vec<u8>; N]) -> Vec<u8> {
    let mut value = Vec::new();
    for field in fields {
        value.extend_from_slice(&field);
    }
    Any::new(Tag::Sequence, value).unwrap().to_der().unwrap()
}

fn explicit_integer(tag: u32, value: i32) -> Vec<u8> {
    explicit_tag(tag, &value.to_der().unwrap())
}

fn explicit_octet(tag: u32, value: &[u8]) -> Vec<u8> {
    let inner = Any::new(Tag::OctetString, value.to_vec())
        .unwrap()
        .to_der()
        .unwrap();
    explicit_tag(tag, &inner)
}

fn explicit_tag(tag: u32, inner: &[u8]) -> Vec<u8> {
    Any::new(
        Tag::ContextSpecific {
            constructed: true,
            number: TagNumber(tag),
        },
        inner.to_vec(),
    )
    .unwrap()
    .to_der()
    .unwrap()
}

fn root_of_trust(key: [u8; 32], hash: [u8; 32]) -> Vec<u8> {
    sequence([
        Any::new(Tag::OctetString, key.to_vec())
            .unwrap()
            .to_der()
            .unwrap(),
        true.to_der().unwrap(),
        Any::new(Tag::Enumerated, vec![0])
            .unwrap()
            .to_der()
            .unwrap(),
        Any::new(Tag::OctetString, hash.to_vec())
            .unwrap()
            .to_der()
            .unwrap(),
    ])
}

fn split(mut bytes: &[u8]) -> Result<Vec<Vec<u8>>, der::Error> {
    let mut out = Vec::new();
    while !bytes.is_empty() {
        let (_, rest) = AnyRef::from_der_partial(bytes)?;
        let used = bytes.len() - rest.len();
        out.push(bytes[..used].to_vec());
        bytes = rest;
    }
    Ok(out)
}
