//! Microbenchmark suite for attestation-core.

use criterion::{black_box, criterion_group, criterion_main, Criterion};

/// Benchmarks minimal attestation measurement overhead.
fn bench_attestation(c: &mut Criterion) {
    c.bench_function("attestation_overhead", |b| {
        b.iter(|| {
            black_box(42);
        })
    });
}

criterion_group!(benches, bench_attestation);
criterion_main!(benches);
