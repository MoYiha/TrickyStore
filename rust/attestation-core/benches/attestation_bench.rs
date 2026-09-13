use criterion::{black_box, criterion_group, criterion_main, Criterion};

fn bench_attestation(c: &mut Criterion) {
    c.bench_function("attestation_overhead", |b| b.iter(|| {
        black_box(42);
    }));
}

criterion_group!(benches, bench_attestation);
criterion_main!(benches);
