//! Microbenchmark suite for attestation-core.

use cleverestricky_attestation_core::{
    inspect_captured_patch_levels, rewrite_extension, PatchLevels, RewriteRequest,
};
use criterion::{black_box, criterion_group, criterion_main, Criterion};

const BOOT_KEY: [u8; 32] = [0x11; 32];
const BOOT_HASH: [u8; 32] = [0x22; 32];

/// Benchmarks representative public attestation-core operations.
fn bench_attestation(c: &mut Criterion) {
    let payload = [0x30, 0x03, 0x02, 0x01, 0x00];
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
    c.bench_function("rewrite_extension_validation", |b| {
        b.iter(|| {
            let _ = rewrite_extension(black_box(&req));
        })
    });
}

criterion_group!(benches, bench_attestation);
criterion_main!(benches);
