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
│                        └──────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

The C++ `binder_interceptor` remains the injection entry point (loaded via
Zygisk or ptrace). It delegates CBOR encoding and COSE/RKP operations to the
Rust library through a C FFI boundary defined in
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
    └── ffi.rs                        # extern "C" FFI bridge
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
rust_free_buffer(info);
```

## CI

Rust tests run automatically on push/PR via `.github/workflows/rust-tests.yml`.
