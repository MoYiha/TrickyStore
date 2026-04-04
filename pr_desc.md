💡 What: Replaced `format!` macro with `String::with_capacity` and `push`/`push_str` in `get_all_fingerprints`.
🎯 Why: In Rust, avoiding `format!` for simple string concatenation prevents heavy runtime formatting machinery overhead.
📊 Impact: Improves execution speed and reduces binary bloat.
🔬 Measurement: Verify compilation size and microbenchmark the `get_all_fingerprints` string creation logic.
