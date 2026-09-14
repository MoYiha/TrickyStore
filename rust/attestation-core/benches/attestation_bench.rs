//! Microbenchmark suite for attestation-core.

use cleverestricky_attestation_core::{
    inspect_captured_patch_levels, rewrite_extension, PatchLevels, RewriteRequest,
};
use criterion::{black_box, criterion_group, criterion_main, Criterion};
use der::asn1::Any;
use der::{Encode, Tag};

const ROOT_OF_TRUST_TAG: u32 = 704;
const BOOT_KEY: [u8; 32] = [0x11; 32];
const BOOT_HASH: [u8; 32] = [0x22; 32];

fn sequence<const N: usize>(fields: [Vec<u8>; N]) -> Vec<u8> {
    let mut value = Vec::new();
    for field in fields {
        value.extend_from_slice(&field);
    }
    Any::new(Tag::Sequence, value).unwrap().to_der().unwrap()
}

fn explicit_tag(tag: u32, inner: &[u8]) -> Vec<u8> {
    Any::new(
        Tag::ContextSpecific {
            constructed: true,
            number: der::TagNumber(tag),
        },
        inner.to_vec(),
    )
    .unwrap()
    .to_der()
    .unwrap()
}

fn sample_key_description() -> Vec<u8> {
    let raw_key = [0x33u8; 32];
    let raw_hash = [0x44u8; 32];
    let unlocked_root = sequence([
        Any::new(Tag::OctetString, raw_key.to_vec())
            .unwrap()
            .to_der()
            .unwrap(),
        false.to_der().unwrap(),
        Any::new(Tag::Enumerated, vec![2])
            .unwrap()
            .to_der()
            .unwrap(),
        Any::new(Tag::OctetString, raw_hash.to_vec())
            .unwrap()
            .to_der()
            .unwrap(),
    ]);
    let tee = sequence([explicit_tag(ROOT_OF_TRUST_TAG, &unlocked_root)]);
    let software = sequence([]);
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
        software,
        tee,
    ])
}

/// Benchmarks representative public attestation-core operations.
fn bench_attestation(c: &mut Criterion) {
    let payload = sample_key_description();
    c.bench_function("inspect_captured_patch_levels", |b| {
        b.iter(|| {
            let _ = inspect_captured_patch_levels(black_box(&payload));
        })
    });

    let req = RewriteRequest {
        extension_der: &payload,
        patch_levels: PatchLevels::default(),
        id_overrides: &[],
        module_hash: None,
        verified_boot_key: &BOOT_KEY,
        verified_boot_hash: &BOOT_HASH,
    };
    c.bench_function("rewrite_extension", |b| {
        b.iter(|| {
            let _ = rewrite_extension(black_box(&req));
        })
    });
}

criterion_group!(benches, bench_attestation);
criterion_main!(benches);
