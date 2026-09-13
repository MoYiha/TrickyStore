//! Microbenchmark suite for crypto-core.

use criterion::{black_box, criterion_group, criterion_main, Criterion};

/// Benchmarks minimal crypto measurement overhead.
fn bench_crypto(c: &mut Criterion) {
    c.bench_function("crypto_overhead", |b| {
        b.iter(|| {
            black_box(42);
        })
    });
}

criterion_group!(benches, bench_crypto);
criterion_main!(benches);
