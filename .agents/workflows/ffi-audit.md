---
description: How to perform an FFI safety and codebase audit for CleveresTricky
---

# FFI & Codebase Safety Audit Workflow

This workflow documents the process for auditing the Rust FFI layer, C++ binder interceptor, Kotlin service, and shell scripts for safety, security, and correctness issues.

## Pre-Audit Checks

// turbo
1. Pull latest changes:
```bash
git pull origin master
```

## Step 1: Rust FFI Audit

2. Check all `extern "C"` functions in `rust/cbor-cose/src/ffi.rs`:
   - Every FFI function MUST be wrapped in `std::panic::catch_unwind`
   - No `unwrap()` or `expect()` calls inside FFI functions (only in `#[cfg(test)]`)
   - All buffer returns must use `RustBuffer::from_vec()` (which uses `into_boxed_slice`)
   - `rust_free_buffer` must reconstruct `Box<[u8]>` from raw pointer + length

3. Verify pointer validation via `validate_slice_args()`:
   - Null pointer check
   - Alignment check
   - Size overflow check (`len * size_of::<T>()` <= `isize::MAX`)
   - Address overflow check (`ptr + size` no wrap)

// turbo
4. Run Rust tests:
```bash
cd rust/cbor-cose && cargo test --verbose && cargo clippy -- -D warnings
```

## Step 2: SELinux Policy Audit

5. Check `module/template/sepolicy.rule`:
   - **EVERY `allow` statement MUST end with a semicolon (`;`)**
   - Missing semicolons will silently break policy compilation on device
   - Check `type` declarations are properly formatted

## Step 3: C++ Binder Interceptor Audit

6. Check `module/src/main/cpp/binder_interceptor.cpp`:
   - `rust_free_buffer` called after every `rust_prop_get` / `rust_*` call
   - `is_binder_fd` caches BOTH positive AND negative results
   - `strncpy` always followed by null termination
   - Thread safety: `std::shared_mutex` used correctly for shared state

## Step 4: Kotlin Service Audit

7. Check all `*.kt` files in `service/src/main/java/`:
   - No `java.util.Random` for crypto/key material — use `java.security.SecureRandom`
   - Log messages reference correct variables (e.g., `$strongBox` not `$tee`)
   - Binder transaction codes match between C++ and Kotlin
   - `Parcel.obtain()` always matched with `recycle()` in finally blocks

## Step 5: Shell Script Audit

8. Check `module/template/service.sh`, `customize.sh`, `post-fs-data.sh`:
   - Shell syntax is valid (`bash -n script.sh`)
   - SELinux contexts are applied (`chcon`)
   - File permissions are restrictive (700 for dirs, 600 for config files)
   - `while true` preferred over `while [ true ]`

## Step 6: Security Checks

9. Scan for:
   - Hardcoded secrets/passwords/API keys in production code
   - `unwrap()`/`expect()` near FFI boundaries
   - Weak cryptographic algorithms

## Known Issues & Historical Bugs

### RustBuffer UB (Fixed)
The original `Vec<u8>` to raw pointer conversion had `capacity != length`, causing `rust_free_buffer` to deallocate with wrong size. Fixed by `RustBuffer::from_vec` using `Vec::into_boxed_slice()`.

### SELinux Semicolons (Fixed 2026-03-07)
12 `allow` statements in `sepolicy.rule` were missing semicolons, preventing policy compilation on real devices.

### Insecure PRNG (Fixed 2026-03-07)
`LocalRkpProxy.kt` used `java.util.Random` for HMAC key fallback instead of `SecureRandom`.
