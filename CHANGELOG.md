# Changelog

## V2.6.0

### What changed

This update focuses on making everyday use more reliable and predictable: cleaner startup, safer keybox handling, better recovery when something fails, clearer WebUI errors, and a more polished companion app.

### Reliability and WebUI

- **Improved startup and reboot reliability.** The module is less likely to end up partially running after boot or after one of its background components restarts.
- **Fixed cases where WebUI could report that the Android adapter was unavailable.** Runtime Health, resource information and file operations now have a more reliable startup path.
- **Improved automatic recovery.** If a background component stops unexpectedly, the module can rebuild the affected runtime state instead of requiring a full reinstall.
- **Improved large file and response handling in WebUI.** Transfers are more reliable and failures are reported more clearly.
- **Improved diagnostics.** When something cannot be applied, the UI is more likely to show the real failure instead of leaving the module in an unclear half-working state.

### Attestation and performance

- **Fixed a timing difference found during real-device testing.** Normal keys no longer do unnecessary attestation work during creation or later readback.
- **Kept the fix performance-based rather than hiding the issue.** No artificial delay, timing padding or relaxed detection threshold was added.
- **Reduced unnecessary work on frequently used attestation paths** while preserving the existing Android key and cryptographic behavior.
- **Improved handling of Root of Trust, patch-level, device-ID and module-related attestation data** without changing normal key usage.

### Keybox safety

- **Made keybox updates safer.** A new keybox is only treated as active after it has been applied successfully.
- **Preserved the previous working keybox when an update fails.** A bad upload should no longer replace a known-good setup.
- **Improved validation for malformed, oversized or mismatched keyboxes** before they can affect the active configuration.
- **Improved EC, RSA and multi-key keybox handling** while keeping existing supported formats compatible.
- **Improved failure reporting** so invalid input and activation problems are easier to distinguish.

### Companion Encryptor

- **Redesigned the app with a monochrome black-and-white interface.** The previous default purple styling has been removed.
- **Improved the create/import flow** with a cleaner full-screen layout, better keyboard behavior and clearer actions.
- **Added an in-app language selector** with English, Arabic, German, Spanish, Hindi, Indonesian, Russian, Turkish and Simplified Chinese, plus system-default behavior.
- **Improved validation before encryption.** Invalid keybox files are rejected earlier with clearer feedback.
- **Improved import, export, overwrite and delete flows** and made storage-related failures easier to understand.
- **Reduced UI stalls** by moving file and metadata work away from the main interface thread.
- **Kept sensitive vault data local** with screen-capture protection, no-backup storage rules and the existing strong encryption settings.

### Security and installation

- **Tightened access around sensitive files and key material** and reduced the amount of privileged work needed during normal operation.
- **Improved protection against unsafe paths, links, malformed files and oversized inputs.**
- **Hardened installation and update checks.** Missing or damaged module files are more likely to be caught before producing a broken installation.
- **Expanded automated regression coverage** for startup, WebUI, keybox activation, attestation behavior and final module packaging.

### Compatibility

- Existing supported CBOX and encrypted-backup files remain readable.
- Existing EC, RSA and multi-key keybox workflows remain supported.
- KernelSU and APatch module packaging remain supported.
- Existing user configuration and normal module usage are intended to remain familiar.

## V2.5.8

- Fixed Restore Defaults enabling Automatic Security Patch on current ROMs when only vendor or boot patch metadata lagged behind.
- Recommended defaults now use the primary system security patch to decide whether Security Patch should be enabled, while system/vendor/boot remain automatic when the feature is actually needed.
- Stabilized early-boot attestation by reusing only fresh, successfully parsed Google attestation revocation state across daemon restarts and reboots.
- Kept Global Mode and Automatic Keybox Check enabled by default while optional identity/privacy features remain disabled.
