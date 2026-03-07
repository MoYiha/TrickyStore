# FFI Safety Audit Notes

> **Audit Date:** 2026-03-07  
> **Auditor:** Automated Systems Analysis  
> **Scope:** All `extern "C"` functions in `rust/cbor-cose/src/ffi.rs` and their consumers

---

## 1. Memory Ownership Model

### RustBuffer Lifecycle

```
Rust allocates (from_vec) → C++ receives → C++ uses → C++ calls rust_free_buffer
```

| Step | Mechanism | Safety Guarantee |
|------|-----------|-----------------|
| Allocation | `Vec::into_boxed_slice()` → `Box::into_raw()` | `capacity == length`; no excess allocation metadata |
| Transport | `RustBuffer { data, len }` passed by value | C struct, no hidden vtable or destructor |
| Deallocation | `Box::from_raw(slice_from_raw_parts_mut(data, len))` | Exact reversal of allocation; zero UB risk |

### ⚠️ Rules for C++ Consumers

1. **NEVER** call `free()` / `delete` on `RustBuffer.data` — it was allocated by Rust's global allocator.
2. **ALWAYS** call `rust_free_buffer(buf)` exactly once per non-empty buffer.
3. It is safe to call `rust_free_buffer` on an empty buffer (`data == nullptr, len == 0`).

---

## 2. Panic Safety

Every `#[no_mangle] pub extern "C"` function is wrapped in `std::panic::catch_unwind`:

| Function | Return on Panic |
|----------|----------------|
| `rust_cbor_encode_unsigned` | `RustBuffer::empty()` |
| `rust_cbor_encode_int` | `RustBuffer::empty()` |
| `rust_cbor_encode_bytes` | `RustBuffer::empty()` |
| `rust_cbor_encode_text` | `RustBuffer::empty()` |
| `rust_generate_maced_public_key` | `RustBuffer::empty()` |
| `rust_create_device_info` | `RustBuffer::empty()` |
| `rust_create_certificate_request` | `RustBuffer::empty()` |
| `rust_generate_spoofed_bcc` | `RustBuffer::empty()` |
| `rust_generate_keymint_exploit_payload` | `RustBuffer::empty()` |
| `rust_fp_inject` | `0` |
| `rust_fp_fetch` | `0` |
| `rust_fp_get` | `RustBuffer::empty()` |
| `rust_fp_count` | `0` |
| `rust_fp_clear` | (no-op) |
| `rust_kick_already_blocked_ioctls` | (no-op) |
| `rust_start_race_engine` | (no-op) |
| `rust_prop_get` | `RustBuffer::empty()` |
| `rust_prop_set` | (no-op) |
| `rust_free_buffer` | (no-op) |

**Result:** A panic in any Rust code path will **never** unwind across the FFI boundary.

---

## 3. `unwrap()` / `expect()` Audit

- **Zero** `unwrap()` or `expect()` calls exist in any `extern "C"` function.
- All `unwrap()` calls in the Rust crate are confined to `#[cfg(test)]` blocks only.

---

## 4. Pointer Validation

All pointer-accepting FFI functions use `validate_slice_args()` (ffi.rs:28-48) which checks:

1. **Null pointer** — returns `None` (empty slice for `len == 0`).
2. **Alignment** — rejects misaligned pointers.
3. **Overflow** — checks `len * size_of::<T>()` does not overflow `isize::MAX`.
4. **Address overflow** — checks `ptr + size` does not wrap.

---

## 5. JNI Reference Leaks

**Not applicable.** This is a pure C++ → Rust static-library FFI bridge. No JNI calls exist in the Rust crate. The Kotlin/Java service layer communicates via Android Binder IPC, not direct JNI to Rust.

---

## 6. C++ Consumer Audit (`binder_interceptor.cpp`)

### `new_system_property_get` (line 154-233)
- ✅ Calls `rust_prop_get` → uses buffer → calls `rust_free_buffer` on both success and cache-miss paths.
- ✅ Calls `rust_prop_set` to populate cache on Binder fallback success.
- ✅ `strncpy` with `PROP_VALUE_MAX - 1` and explicit null termination.

### `entry()` (line 692-715)
- ✅ `rust_generate_spoofed_bcc` and `rust_generate_keymint_exploit_payload` results freed immediately.

---

## 7. Thread Safety

| Module | Mechanism |
|--------|-----------|
| `properties.rs` | `RwLock<Option<AHashMap>>` — poisoned lock returns `None` |
| `fingerprint.rs` | `RwLock<Option<FingerprintCache>>` — same pattern |
| `binder_interceptor.cpp` | `std::shared_mutex` for binder FD cache |

All `RwLock` operations use `if let Ok(guard)` to gracefully handle poisoned locks.

---

## 8. Historical Bug Reference (PR #447)

The original PR #447 described a UB pattern where `Vec<u8>` was converted to a raw pointer with `capacity ≠ length`:

```rust
// ❌ FORMER BUG (capacity ≠ length → wrong dealloc size)
let ptr = vec.as_mut_ptr();
let len = vec.len();
std::mem::forget(vec);
RustBuffer { data: ptr, len }
```

This was fixed by introducing `RustBuffer::from_vec` which uses `into_boxed_slice`:

```rust
// ✅ CURRENT CODE (capacity == length guaranteed)
fn from_vec(v: Vec<u8>) -> Self {
    let mut boxed = v.into_boxed_slice();
    let data = boxed.as_mut_ptr();
    let len = boxed.len();
    std::mem::forget(boxed);
    RustBuffer { data, len }
}
```

**Status:** Fixed. All FFI functions use `RustBuffer::from_vec`.
