# Changelog

## V2.6.0

### A major reliability, security, and architecture release

CleveresTricky 2.6.0 is much larger than a normal feature update. Compared with 2.5.8, a large part of the module's internal runtime has been rebuilt so that failures are easier to recover from, privileged work is more isolated, keybox handling is safer, and the WebUI and companion app are less likely to leave the module in a half-working state.

Most users should not need to learn the new architecture. The practical goal is simple: **the module should start more reliably, fail more clearly, recover more safely, and keep sensitive key material behind tighter boundaries.**

### Runtime and startup reliability

- **Reworked the module's background runtime into supervised processes.** If an Android adapter or backend worker dies, the long-lived service can recover it with bounded restart behavior instead of silently leaving WebUI/runtime features unavailable.
- **Fixed Android adapter startup races.** Early secure-file requests now tolerate the short process-publication window instead of killing the adapter before it can register with the WebUI service.
- **Fixed a real WebUI startup deadlock.** The Android WebUI bridge now becomes reachable before waiting on the heavier backend readiness path, so diagnostics are still available when another runtime component is unhealthy.
- **Fixed WebUI staging permissions.** The secure file broker now explicitly supports the required WebUI staging area without opening arbitrary filesystem access.
- **Fixed large WebUI response streaming.** Staged downloads now use bounded chunked writes instead of treating a maximum-size limit as an exact expected length.
- **Improved backend restart recovery.** Stale process identities and stale opaque key handles are rejected after a restart, and runtime state is rebuilt through a controlled recovery path.
- **Improved daemon lifecycle handling.** Stale PIDs are invalidated, child generations are tracked correctly, and the supervisor can recreate dependent workers instead of keeping a broken runtime graph alive.

### Attestation and KeyMint behavior

- **Preserved the real Android/KeyMint cryptographic path.** CleveresTricky still rewrites only the attestation certificate result; the private key and normal cryptographic operations remain owned by the platform security level.
- **Moved portable attestation certificate parsing and rewriting into the unprivileged Rust backend.** Android-facing code now keeps a much smaller role around Binder/framework integration.
- **Private key material no longer needs to cross the managed IPC boundary.** Active keyboxes are represented by opaque backend-owned key identifiers instead of transporting PKCS#8 private-key bytes through Android-side IPC.
- **Improved Root of Trust, patch-level, device-ID and module-hash handling.** The Rust certificate path preserves the original certificate structure while applying only the configured compatibility changes.
- **Fixed the 2.6.0 timing regression found during real-device testing at both key creation and readback.** Non-attested `generateKey` replies and targeted `getKeyEntry` readbacks are classified locally by Android's attestation-extension OID before Rust certificate-backend work is considered. In particular, an ordinary non-attested readback no longer pays a Rust UDS certificate-inspection round trip while an attested readback is served from the in-memory replacement cache.
- **Kept the hot Binder path bounded.** No synthetic sleep, delay, threshold change or timing padding was added to hide the difference; the fix removes the asymmetric work instead.

### Keybox safety and recovery

- **Keybox updates are now transactional from the user's point of view.** An upload or activation is only reported as successful after the new active snapshot is actually published.
- **The previous working keybox snapshot is preserved on activation failure.** A bad backend transition no longer turns a failed update into a false-success state.
- **Keybox parsing and validation moved to bounded Rust code.** EC, RSA, multi-key files, malformed input, mismatched keys/certificates and oversized structures are checked before publication.
- **Keybox file access is pinned to trusted directory capabilities.** Privileged code does not reopen arbitrary paths supplied later by another process.
- **Large keybox input crosses privilege boundaries as validated file descriptors instead of privileged path reads or large Base64 payloads.**
- **Failure states are surfaced more clearly.** Backend/keybox activation problems fail closed rather than silently continuing with partially applied state.

### WebUI and diagnostics

- **Reworked WebUI transport around a long-lived bounded local socket protocol.** This replaces the older request/response polling model while keeping large-body staging where needed.
- **Runtime Health and resource diagnostics now have a more reliable path to the Android adapter.** Startup ordering was changed specifically so the UI does not disappear just because another backend component is still recovering.
- **File operations and large responses are bounded and validated.** Traversal, unsafe link handling, oversized payloads and ambiguous staging paths are rejected.
- **Improved failure diagnostics.** CI and runtime paths now preserve useful failure reports instead of collapsing everything into a generic success/failure signal.

### Companion Encryptor / keybox vault

- **Rebuilt the companion app around a local-first secure vault flow.** Keybox XML stays on-device and encrypted vault writes use the native Rust crypto layer.
- **Redesigned the app with a monochrome black-and-white visual system.** The previous default Material-purple look was removed.
- **Improved the create-keybox flow into a proper full-screen, keyboard-safe experience** with clearer hierarchy, spacing and action placement.
- **Moved language selection out of the main content hierarchy.** It now behaves like a compact preference action instead of competing visually with file/size information.
- **Added nine UI languages:** English, Arabic, German, Spanish, Hindi, Indonesian, Russian, Turkish and Simplified Chinese, plus system-default language behavior.
- **Added bounded input validation before encryption.** A file is validated as a usable keybox before the final encrypt/save step instead of accepting arbitrary text and later collapsing the native error into a generic failure.
- **Improved error reporting.** Invalid keybox input, native failures and storage problems can be distinguished instead of always showing only `Encryption failed`.
- **Moved file and metadata I/O off the Compose/UI thread.** Directory checks, file-size metadata and picker reads no longer run during recomposition or block the main thread unnecessarily.
- **Added safer overwrite/import/export/delete behavior, screen-capture protection, no-backup storage rules and bounded 10 MiB input handling.**
- **Preserved strong encryption settings.** Performance work does not reduce PBKDF2 cost or weaken AES-GCM/key handling just to make the UI faster.

### Security hardening

- **Introduced a smaller privileged Rust supervisor.** It keeps only the capabilities needed for protected filesystem/process operations; complex parsing and cryptography run in an unprivileged backend.
- **Added authenticated local IPC and peer/process validation.** Backend and Android-side clients verify the process they are talking to instead of trusting a socket path alone.
- **Hardened native file access.** Sensitive operations use trusted directory capabilities, descriptor-relative opens, no-follow behavior, close-on-exec handling, type checks and bounded reads/writes.
- **Added validated file-descriptor passing.** Ambiguous, truncated or multi-FD ancillary messages are rejected and descriptors are closed safely on errors.
- **Reduced secret lifetime.** Password buffers, decrypted scratch data, recovery keys, temporary key identifiers and other sensitive buffers are zeroized where the platform permits.
- **Kept privileged code away from complex untrusted formats.** XML, JSON, ZIP, certificates, keyboxes, backup plaintext and application payload semantics are handled outside the privileged supervisor.
- **Kept the private libbinder C++ boundary narrow.** The existing Binder ABI island remains the Android-specific exception rather than expanding the amount of first-party C++.

### Installer, packaging, and update safety

- **Hardened module extraction and installation.** Archive structure, extraction targets, permissions and expected runtime files are checked more defensively.
- **Added runtime payload integrity gates to the build.** CI now verifies required binaries/assets, installer wiring, supervisor wiring and minimum expected payload sizes so accidental deletion or abnormal shrinkage cannot quietly produce a "successful" module ZIP.
- **Added checksum/layout verification for the final module archive.** Missing payload hashes, orphan hashes, duplicates, traversal entries and unsafe archive layout are rejected.
- **Added native ELF hardening validation for both supported Android ABIs.**
- **Expanded security regression coverage.** Runtime wiring, WebUI staging, bounded streaming, backend recovery, keybox activation and both `generateKey` and `getKeyEntry` attestation fast paths are now protected by automated tests.
- **Failed JVM test reports are retained as CI artifacts.** This removes the previous blind spot where an enforcement gate could fail without an immediately useful report.

### Performance and resource behavior

- **Removed unnecessary work from the latency-sensitive attestation path.** Ordinary keys stay on the local fast path during both creation and readback; non-attested readback no longer enters Rust certificate inspection, while attested readback can use the already-published replacement cache.
- **Kept policy/profile resolution cached in managed memory** instead of adding an out-of-process lookup to every Binder hot-path call.
- **Avoided new polling loops and unbounded queues.** IPC frames, parser counts, files, staging bodies and caches all have explicit limits.
- **Reduced avoidable Android UI I/O** by moving filesystem/JNI work off the main thread in the companion app.
- Real-device PSS/RSS and end-to-end Binder timing are intentionally not presented as CI numbers. Those measurements depend on device/ROM/kernel state and should be validated on physical hardware.

### Compatibility

- Existing supported CBOX and encrypted-backup formats remain readable; the migration keeps the established KDF/AEAD compatibility behavior.
- Existing EC and RSA keybox workflows remain supported, including multi-key keyboxes.
- Android framework/Binder behavior remains compatible while portable parsing, crypto and recovery logic move behind Rust boundaries.
- KernelSU/APatch module packaging remains supported with stronger validation around the generated archive.
- The architecture changed substantially, but user configuration and normal module usage are intended to remain familiar.

### In short

2.6.0 is not a UI-only release or a simple language/feature update. It replaces a large amount of the module's internal plumbing: process supervision, secure file brokerage, keybox parsing, backup crypto, certificate rewriting, IPC framing, restart recovery, WebUI transport and the companion encryptor's native crypto path. The visible benefit is a module that is **harder to leave half-broken, clearer when something fails, safer around key material, and easier to validate before release.**

## V2.5.8

- Fixed Restore Defaults enabling Automatic Security Patch on current ROMs when only vendor or boot patch metadata lagged behind.
- Recommended defaults now use the primary system security patch to decide whether Security Patch should be enabled, while system/vendor/boot remain automatic when the feature is actually needed.
- Stabilized early-boot attestation by reusing only fresh, successfully parsed Google attestation revocation state across daemon restarts and reboots.
- Kept Global Mode and Automatic Keybox Check enabled by default while optional identity/privacy features remain disabled.
