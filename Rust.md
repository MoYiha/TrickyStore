# Rust Migration — CBOR/COSE Core

## Architecture: Zygisk → C++ entry → Rust core

```
┌─────────────────────────────────────────────────────┐
│  Android Process (Zygote / target app)              │
│                                                     │
│  ┌───────────────┐     ┌──────────────────────────┐ │
│  │ Zygisk/ptrace │────▶│ C++ binder_interceptor   │ │
│  │ (injection)   │     │ (entry point, ioctl hook) │ │
│  └───────────────┘     └──────────┬───────────────┘ │
│                                   │ extern "C" FFI  │
│                        ┌──────────▼───────────────┐ │
│                        │ Rust core library         │ │
│                        │  • CBOR encoder (RFC 8949)│ │
│                        │  • COSE_Mac0 / RKP        │ │
│                        │  • HMAC-SHA256            │ │
│                        │  • Fingerprint cache      │ │
│                        └──────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

The C++ `binder_interceptor` remains the injection entry point (loaded via
Zygisk or ptrace). It delegates CBOR encoding, COSE/RKP operations, and
fingerprint caching to the Rust library through a C FFI boundary defined in
`rust/cbor-cose/include/cleverestricky_cbor_cose.h`.

## Directory layout

```
rust/cbor-cose/
├── Cargo.toml
├── include/
│   └── cleverestricky_cbor_cose.h   # C header for C++ integration
└── src/
    ├── lib.rs                        # Crate root
    ├── cbor.rs                       # CBOR encoder (RFC 8949 canonical)
    ├── cose.rs                       # COSE_Mac0, DeviceInfo, RKP helpers
    ├── ffi.rs                        # extern "C" FFI bridge
    └── fingerprint.rs                # Pixel Beta fingerprint fetcher/cache
```

## Building

```bash
# Run tests
cd rust/cbor-cose
cargo test

# Build static library (for linking into the native module)
cargo build --release
# Output: target/release/libcleverestricky_cbor_cose.a
```

## Release profile (binary size & anti-detection)

The `[profile.release]` in `Cargo.toml` configures:

| Setting | Effect |
|---|---|
| `panic = "abort"` | Removes unwind tables, smaller binary |
| `lto = true` | Whole-program optimization, dead code removal |
| `codegen-units = 1` | Maximum optimization within the crate |
| `strip = "symbols"` | Strips Rust symbol names from the `.a` |

## Symbol visibility / anti-detection strategy

FFI function names (e.g. `rust_cbor_encode_unsigned`) are **not obfuscated
in source code**. Instead, symbol hiding is handled at the linker level by the
existing C++ `CMakeLists.txt` flags:

```cmake
add_compile_options(-fvisibility=hidden ...)
add_link_options(-s -Wl,--gc-sections -Wl,--exclude-libs,ALL)
```

- `-fvisibility=hidden` — C++ symbols default to hidden
- `-s` — strip all symbol tables from the final `.so`
- `--exclude-libs,ALL` — prevents re-exporting symbols from static libs

This means Rust symbols are resolved at link time and **do not appear in the
final loadable binary**. Renaming them in source would be
security-through-obscurity with no actual benefit.

## Buffer ownership (CRITICAL)

```
⚠️  NEVER call free() or delete on a RustBuffer.data pointer.
    ALWAYS use rust_free_buffer() to return memory to Rust.
```

The C/C++ and Rust heaps are separate allocators. Passing a Rust-allocated
pointer to the C `free()` causes undefined behaviour (double-free, heap
corruption, segfault). The `RustBuffer` struct carries both the pointer and
its length so Rust can reconstruct and deallocate it safely.

## C FFI usage from C++

```cpp
#include "cleverestricky_cbor_cose.h"

// Example: encode device info
RustBuffer info = rust_create_device_info(
    (const uint8_t*)"google", 6,
    (const uint8_t*)"Google", 6,
    (const uint8_t*)"husky", 5,
    (const uint8_t*)"Pixel 8 Pro", 11,
    (const uint8_t*)"husky", 5
);
// Use info.data / info.len ...
rust_free_buffer(info);  // ← mandatory, never use free()

// Example: look up a cached fingerprint
RustBuffer fp = rust_fp_get((const uint8_t*)"husky", 5);
if (fp.data) {
    std::string fingerprint((char*)fp.data, fp.len);
    // use fingerprint ...
    rust_free_buffer(fp);
}
```

## Fingerprint updater

The `fingerprint` module (enabled by default via the `fingerprint` Cargo
feature) provides a thread-safe in-memory cache for Pixel Beta fingerprints.

- **Fetch**: `rust_fp_fetch(url, len)` downloads fingerprints via HTTPS
  using `minreq` + `rustls` (pure Rust TLS, no OpenSSL dependency).
- **Inject**: `rust_fp_inject(data, len)` loads fingerprints from raw text.
- **Read**: `rust_fp_get(device, len)` returns the fingerprint for a device
  codename — the C++ hook reads this directly from Rust's RAM cache.
- **Zero file I/O**: Fingerprints live in memory only, reducing the
  filesystem attack surface.

## CI

Rust tests run automatically on push/PR via `.github/workflows/rust-tests.yml`.

## Future: frida-gum

See [`FRIDA_EVALUATION.md`](FRIDA_EVALUATION.md) for an evaluation of
replacing the C++ LSPLT hooking layer with frida-gum's Rust bindings.
