//! Memory-safe CBOR/COSE encoder for CleveresTricky RKP attestation spoofing.
//!
//! Implements RFC 8949 (CBOR) canonical encoding and COSE_Mac0 structures
//! required for Android Remote Key Provisioning (RKP) operations.
//!
//! This Rust implementation mirrors the behavior of the Java `CborEncoder`
//! in `service/src/main/java/cleveres/tricky/cleverestech/util/CborEncoder.java`
//! while providing memory safety guarantees through Rust's borrow checker.

pub mod cbor;
pub mod cose;
