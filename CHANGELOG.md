# Changelog

## V2.7.1

- **Coroutines & Worker Lifecycle Fix:** Fixed a major state corruption bug where `ConflatedRefreshScheduler` leaked a duplicate replacement worker when an exception was thrown. It now correctly allows the existing loop to recover safely.
- **Symlink Traversal & TOCTOU Defense:** Replaced unsafe `File.setLastModified` calls with `Files.setAttribute(..., LinkOption.NOFOLLOW_LINKS)` to prevent Time-Of-Check-to-Time-Of-Use symlink attacks that could inadvertently modify system file timestamps.
- **Cryptographic Memory Leak Fix:** Ensured `KeyboxVerifier` fully zeros out XML cryptographic key streams in RAM. Replaced standard `ByteArrayOutputStream` which only resets counters, with the secure `FastByteArrayOutputStream.wipe()` to explicitly overwrite sensitive data.
- **Concurrency & File I/O Optimization:** Decoupled File I/O writes out of the intrinsic monitor lock (`@Synchronized`) in `ServerManager.saveServers()` to prevent disk writes from blocking concurrent WebUI requests.
- **Idle Thread & Memory Optimization:** Eradicated unbounded manual `Thread` creation in `InstalledPackagesCompat` and `RuntimeDiagnostics`. Replaced them with bounded, idle-timeout `ThreadPoolExecutor` instances to reduce memory allocations and prevent thread exhaustion during rapid enumeration bursts.
- **Process Lifecycle & Orphan Process Prevention:** Guarded all native hook and property command processes across camera, DRM, keystore, telephony, identity applier, and keybox cleaner with strict `try / finally` termination guarantees to eliminate zombie or orphan process risks.
- **Serial Number Parity & Live Identity Rollback:** Extended build properties to include all OEM, ODM, Vendor, RIL, and GSM serial properties (`ro.vendor.serialno`, `ro.odm.serialno`, `vendor.serialno`, `vendor.boot.serialno`, `persist.sys.serialno`, `ro.ril.oem.sno`, `ro.ril.oem.psno`, `sys.serialno`, `gsm.serial`) in both boot spoofing and live runtime rollback snapshots.
- **Identity Override Robustness:** Added direct fallback support for unprefixed `SERIAL`, `IMEI`, and `MEID` configurations in `spoof_build_vars` alongside `ATTESTATION_ID_*` tags.
- **Memory & Resource Hardening:** Optimized network response handling with bounded, zeroized buffers (`FastByteArrayOutputStream`), zero-allocation UTF-8 byte counting, and timed thread retirement for idle WebUI server threads.
- **Procfs PID Discovery Hardening:** Hardened `/proc` process scanning loops against unexpected directory entries to prevent unhandled number formatting exceptions.