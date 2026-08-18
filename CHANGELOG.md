# Changelog

## V2.6.0

### A major rebuild focused on reliability, security, and safer recovery

CleveresTricky 2.6.0 is one of the largest architectural updates in the project's history. Most of the complexity is intentionally hidden from users: everyday installation, keybox management, background recovery, backups, and the companion encryptor should feel more predictable while failures are handled more safely.

### What users get

- **More resilient background services.** Core service processes are now supervised so worker failures can recover without unnecessarily taking down the long-lived service endpoint.
- **Safer keybox changes.** A keybox update is only reported as successful after the new active snapshot is actually committed. If publication fails, the previous working snapshot is preserved instead of returning a false success.
- **A rebuilt mobile keybox vault.** The encryptor app now uses a modern secure vault flow with bounded XML input, native Rust cryptography, explicit import/export/delete handling, safer overwrite behavior, screen-capture protection, and no-backup storage for sensitive local data.
- **Nine UI languages.** The mobile experience is available in English, Arabic, German, Spanish, Hindi, Indonesian, Russian, Turkish, and Simplified Chinese.
- **A harder-to-break installer.** Module installation and updates received stricter archive/layout validation, safer extraction, stronger permission handling, and more defensive backup/restore behavior.
- **Stronger native file protections.** Sensitive native operations are constrained to trusted locations and use stricter path, descriptor, and peer-validation rules.
- **Better failure reporting.** Security-sensitive operations now fail closed and surface real errors instead of silently continuing with partially applied state.

### What changed under the hood

The service architecture is now substantially **Rust-first**. Portable parsing, cryptography, IPC framing, secure filesystem work, recovery logic, backend authentication, and process supervision have moved behind narrow Rust boundaries. Android/Kotlin remains where it is the right fit: lifecycle integration, framework APIs, UI, Binder-facing adapters, and hot-path snapshots.

A privileged Rust supervisor now owns the minimum capabilities needed for protected filesystem and process operations, while complex parsing and cryptographic work runs in an unprivileged Rust backend. Communication is bounded and authenticated, file access is increasingly descriptor-relative, and stale process identities/handles are rejected after restarts.

This is not a cosmetic rewrite. It changes how the module is split internally, how failures recover, how privileged work is isolated, how key material crosses process boundaries, and how the mobile encryptor stores and processes sensitive data. The goal is a simpler user experience on top of a much stricter security and reliability model.

### Compatibility notes

- Existing CBOX/backup compatibility is retained, including older supported formats.
- The Android/Binder-facing behavior remains compatible while portable work is moved out of the managed hot path.
- The existing private libbinder C++ ABI boundary remains intentionally narrow rather than being expanded by this migration.
- Device-only RAM and Binder timing numbers are not claimed here; those measurements require real-device validation rather than CI estimates.

## V2.5.8

- Fixed Restore Defaults enabling Automatic Security Patch on current ROMs when only vendor or boot patch metadata lagged behind.
- Recommended defaults now use the primary system security patch to decide whether Security Patch should be enabled, while system/vendor/boot remain automatic when the feature is actually needed.
- Stabilized early-boot attestation by reusing only fresh, successfully parsed Google attestation revocation state across daemon restarts and reboots.
- Kept Global Mode and Automatic Keybox Check enabled by default while optional identity/privacy features remain disabled.
