# Changelog

## V2.7.1

- **Security & Buffer Hardening:** Reinforced cryptographic buffer zeroization in memory, eliminated TOCTOU symlink traversal risks during configuration updates, and strictly aligned package rule syntax with prefix wildcard semantics.
- **Process & Thread Lifecycle:** Guarded native hook and background command processes against orphan leaks, and replaced unbounded manual thread creation with bounded, idle-timeout thread pools.
- **Performance & Concurrency:** Decoupled file I/O operations from WebUI request-handling monitor locks and optimized network stream buffers for reduced memory allocation.
- **Serial Number & Identity Parity:** Extended property spoofing across OEM, ODM, Vendor, RIL, and GSM serial properties (`ro.vendor.serialno`, `ro.odm.serialno`, `vendor.serialno`, `persist.sys.serialno`, `sys.serialno`, etc.) for full boot and live runtime rollback parity.
- **Identity Override Robustness:** Added direct fallback support for unprefixed `SERIAL`, `IMEI`, and `MEID` configurations in `spoof_build_vars` alongside `ATTESTATION_ID_*` tags.
- **Procfs PID Discovery Hardening:** Hardened `/proc` process scanning loops against unexpected non-numeric directory entries.