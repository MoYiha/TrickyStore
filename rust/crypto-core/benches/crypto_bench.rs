//! Microbenchmark suite for crypto-core.

use cleverestricky_crypto_core::{has_supported_cbox_header, verify_cbox_signature, CboxPayload};
use criterion::{criterion_group, criterion_main, Criterion};
use std::hint::black_box;

const EC_PUBLIC_KEY: &str =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE1JxPSOlrdyKm0raMMZTeiV0WevPD6Nol0UdzGsWfpkwkz8HS3WaT1weN7FrMFimvq4QUJq9pwZ0hrO6/cy++Pg==";
const EC_V1_SIGNATURE: &str =
    "MEQCICfCTlaCRDuo9cg1SFXnf/u4Qict9SOgM3u28HoXNYtpAiBOkVG0WCmDfgMMe3Z0qIO/RtgB0D5Ca4B0IWM3CY+VMA==";

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

    let valid_payload = CboxPayload {
        author: "Δ-author".to_string(),
        xml_content: "<AndroidAttestation NumberOfKeyboxes=\"0\"/>".to_string(),
        signature_base64: EC_V1_SIGNATURE.to_string(),
        signature_version: 1,
    };
    c.bench_function("verify_cbox_signature_valid", |b| {
        b.iter(|| {
            let _ = verify_cbox_signature(black_box(&valid_payload), black_box(EC_PUBLIC_KEY));
        })
    });

    let invalid_payload = CboxPayload {
        author: "benchmark-mismatch".to_string(),
        xml_content: "<xml/>".to_string(),
        signature_base64: EC_V1_SIGNATURE.to_string(),
        signature_version: 1,
    };
    c.bench_function("verify_cbox_signature_invalid", |b| {
        b.iter(|| {
            let _ = verify_cbox_signature(black_box(&invalid_payload), black_box(EC_PUBLIC_KEY));
        })
    });
}

criterion_group!(benches, bench_crypto);
criterion_main!(benches);
