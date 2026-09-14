//! Microbenchmark suite for crypto-core.

use cleverestricky_crypto_core::{has_supported_cbox_header, verify_cbox_signature, CboxPayload};
use criterion::{black_box, criterion_group, criterion_main, Criterion};

/// Benchmarks representative public crypto-core operations.
fn bench_crypto(c: &mut Criterion) {
    let header = [
        b'C', b'B', b'O', b'X', 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    ];
    c.bench_function("has_supported_cbox_header", |b| {
        b.iter(|| {
            let _ = has_supported_cbox_header(black_box(&header));
        })
    });

    let payload = CboxPayload {
        author: "benchmark".to_string(),
        xml_content: "<xml/>".to_string(),
        signature_base64: "invalid_sig".to_string(),
        signature_version: 2,
    };
    c.bench_function("verify_cbox_signature_invalid", |b| {
        b.iter(|| {
            let _ = verify_cbox_signature(
                black_box(&payload),
                black_box("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE"),
            );
        })
    });
}

criterion_group!(benches, bench_crypto);
criterion_main!(benches);
