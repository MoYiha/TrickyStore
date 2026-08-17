// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_attestation_core::{
    rewrite_extension, AttestationIdOverride, PatchLevels, RewriteRequest,
};
use der::asn1::Any;
use der::{Decode, Encode, Tag, TagNumber, Tagged};

const ROOT_OF_TRUST_TAG: u32 = 704;
const MODULE_HASH_TAG: u32 = 724;
const SECOND_IMEI_TAG: u32 = 723;
const UNKNOWN_TAG: u32 = 900;
const BOOT_KEY: [u8; 32] = [0x11; 32];
const BOOT_HASH: [u8; 32] = [0x22; 32];

#[test]
fn preserves_unknown_authorization_tlv_byte_for_byte() {
    let unknown = explicit_octet(UNKNOWN_TAG, b"opaque-unknown-value");
    let tee = auth_list([
        explicit_tag(ROOT_OF_TRUST_TAG, &root_of_trust([1; 32], [2; 32])),
        unknown.clone(),
    ]);
    let extension = key_description(400, 400, auth_list([]), tee);

    let rewritten = rewrite(&extension, &[], None);
    let rewritten_unknown = find_tagged(&rewritten, UNKNOWN_TAG).expect("unknown tag must survive");
    assert_eq!(rewritten_unknown, unknown);
}

#[test]
fn second_imei_is_replaced_only_when_original_tag_exists() {
    let tee = auth_list([
        explicit_tag(ROOT_OF_TRUST_TAG, &root_of_trust([1; 32], [2; 32])),
        explicit_octet(SECOND_IMEI_TAG, b"old-imei2"),
    ]);
    let extension = key_description(400, 400, auth_list([]), tee);
    let ids = [AttestationIdOverride {
        tag: SECOND_IMEI_TAG,
        value: b"new-imei2",
    }];

    let rewritten = rewrite(&extension, &ids, None);
    assert_eq!(
        decode_tagged_octets(&rewritten, SECOND_IMEI_TAG).as_deref(),
        Some(b"new-imei2".as_slice()),
    );

    let without_original = key_description(
        400,
        400,
        auth_list([]),
        auth_list([explicit_tag(
            ROOT_OF_TRUST_TAG,
            &root_of_trust([1; 32], [2; 32]),
        )]),
    );
    let rewritten = rewrite(&without_original, &ids, None);
    assert!(find_tagged(&rewritten, SECOND_IMEI_TAG).is_none());
}

#[test]
fn supported_module_hash_is_preserved_when_no_override_is_supplied() {
    let original_module = explicit_octet(MODULE_HASH_TAG, b"original-module-hash");
    let tee = auth_list([explicit_tag(
        ROOT_OF_TRUST_TAG,
        &root_of_trust([1; 32], [2; 32]),
    )]);
    let software = auth_list([original_module.clone()]);
    let extension = key_description(400, 400, software, tee);

    let rewritten = rewrite(&extension, &[], None);
    assert_eq!(
        find_tagged(&rewritten, MODULE_HASH_TAG).expect("module hash must survive"),
        original_module,
    );
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

fn find_tagged(extension: &[u8], target: u32) -> Option<Vec<u8>> {
    let top = Any::from_der(extension).unwrap();
    let fields = split(top.value()).unwrap();
    for list_index in [6usize, 7usize] {
        let list = Any::from_der(&fields[list_index]).unwrap();
        for encoded in split(list.value()).unwrap() {
            let any = Any::from_der(&encoded).unwrap();
            if let Tag::ContextSpecific { number, .. } = any.tag() {
                if number.value() == target {
                    return Some(encoded);
                }
            }
        }
    }
    None
}

fn decode_tagged_octets(extension: &[u8], target: u32) -> Option<Vec<u8>> {
    let encoded = find_tagged(extension, target)?;
    let outer = Any::from_der(&encoded).unwrap();
    let inner = Any::from_der(outer.value()).unwrap();
    assert_eq!(inner.tag(), Tag::OctetString);
    Some(inner.value().to_vec())
}

fn split(mut bytes: &[u8]) -> Result<Vec<Vec<u8>>, der::Error> {
    let mut out = Vec::new();
    while !bytes.is_empty() {
        let (_, rest) = der::asn1::AnyRef::from_der_partial(bytes)?;
        let used = bytes.len() - rest.len();
        out.push(bytes[..used].to_vec());
        bytes = rest;
    }
    Ok(out)
}
