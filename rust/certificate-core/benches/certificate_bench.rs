use criterion::{black_box, criterion_group, criterion_main, Criterion};

fn bench_certificate(c: &mut Criterion) {
    c.bench_function("certificate_overhead", |b| {
        b.iter(|| {
            black_box(42);
        })
    });
}

criterion_group!(benches, bench_certificate);
criterion_main!(benches);
