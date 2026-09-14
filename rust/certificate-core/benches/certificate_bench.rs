//! Microbenchmark suite for certificate-core.

use cleverestricky_certificate_core::{generate_ec_p256_keypair, is_ec_p256_spki};
use criterion::{black_box, criterion_group, criterion_main, Criterion};

/// Benchmarks representative public certificate-core operations.
fn bench_certificate(c: &mut Criterion) {
    let keypair = generate_ec_p256_keypair().expect("valid keypair");
    let spki = keypair.public_key_spki_der.clone();

    c.bench_function("is_ec_p256_spki", |b| {
        b.iter(|| {
            let _ = is_ec_p256_spki(black_box(&spki));
        })
    });

    c.bench_function("generate_ec_p256_keypair", |b| {
        b.iter(|| {
            let _ = generate_ec_p256_keypair();
        })
    });
}

criterion_group!(benches, bench_certificate);
criterion_main!(benches);
