# Changelog

## V2.7.2

- **Process Lifecycle & Orphan Process Prevention:** Guarded all native hook and property command processes across camera, DRM, keystore, telephony, identity applier, and keybox cleaner with strict `try / finally` termination guarantees to eliminate zombie or orphan process risks.
- **Serial Number Parity & Live Identity Rollback:** Extended build properties to include all OEM, ODM, Vendor, RIL, and GSM serial properties (`ro.vendor.serialno`, `ro.odm.serialno`, `vendor.serialno`, `vendor.boot.serialno`, `persist.sys.serialno`, `ro.ril.oem.sno`, `ro.ril.oem.psno`, `sys.serialno`, `gsm.serial`) in both boot spoofing and live runtime rollback snapshots.
- **Identity Override Robustness:** Added direct fallback support for unprefixed `SERIAL`, `IMEI`, and `MEID` configurations in `spoof_build_vars` alongside `ATTESTATION_ID_*` tags.
- **Memory & Resource Hardening:** Optimized network response handling with bounded, zeroized buffers (`FastByteArrayOutputStream`), zero-allocation UTF-8 byte counting, and timed thread retirement for idle WebUI server threads.
- **Procfs PID Discovery Hardening:** Hardened `/proc` process scanning loops against unexpected directory entries to prevent unhandled number formatting exceptions.