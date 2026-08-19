# Changelog

## V2.6.1

### Keybox storage and verification

- Stored Keyboxes now discovers safe XML keyboxes directly under `/data/adb/cleverestricky` as well as XML/CBOX files in the managed `keyboxes` directory.
- Stored and Verification views show the serial number of certificate #3 when present, instead of treating every source as `keybox.xml`.
- WebUI counts active keybox sources rather than EC/RSA key records inside a keybox, so one multi-key keybox is shown as one keybox.
- Stored Keyboxes supports bulk deletion and five-item pagination.
- Manual verification resolves the Rust CRL generation after keybox parsing, fixing the first-check failure when parsing discovers and recovers a restarted Rust backend.
- Direct-root XML loading remains basename-only, symlink-safe and bounded; duplicate filenames across root and `keyboxes/` fail closed because profile selection is filename-based.
- Module ZIP import follows the module runtime's 64-source bound. The companion app vault remains a separate 10,000-CBOX store.

### Cleveres Encryptor

- Create CBOX accepts one XML keybox or a ZIP batch and processes XML entries one at a time.
- Real keyboxes use certificate #3 serial as the preferred collision-safe CBOX filename, with the existing sanitized source-name fallback when that public certificate is unavailable.
- Vault entries can be selected in bulk, deleted together, or exported together as a ZIP without decrypting the CBOX payloads.

### WebUI structure and Identity controls

- Dashboard is the single owner for feature enable/disable controls.
- Identity keeps its master toggle on Dashboard. Enabling it reveals the Build identity, Attestation identity, Telephony identity, Region identity and Identity refresh child toggles in the same Dashboard card.
- Security Patch is shown inside the expanded Identity group for feature ownership clarity, but it has no separate enable/disable toggle and follows the Identity master state.
- The Identity page contains Identity and Security Patch detail configuration only; the duplicate Identity toggle panel and stale disabled-state banner were removed.
- Security Patch remains removed from the top-level Dashboard cards and navigation while existing policy/profile storage remains compatible.

### Reliability hotfixes

- Fixed the keybox FileObserver refresh loop that could repeatedly trigger WebUI/runtime reload work after the V2.6.1 keybox changes.
- Preserved legacy root-level keybox basename compatibility while keeping managed-directory, symlink and duplicate-name safety rules intact.
- Updated WebUI and Security Regression coverage for the final Dashboard-owned Identity layout so duplicate or misplaced Security Patch controls are caught by CI.

## V2.6.0

### A new core for CleveresTricky

CleveresTricky 2.6.0 is not a small maintenance release. It is a major security and architecture upgrade built to push the TrickyStore ecosystem further: less privileged work, stronger isolation, safer key handling and a much more deliberate trust boundary.

The project is now built as a **security-first TrickyStore fork**, with the goal of setting the security bar for this class of module rather than simply adding more spoofing features.

### Rust-first security architecture

- **Keybox parsing, validation and cryptographic certificate work now live behind an unprivileged Rust backend.** Complex attacker-controlled input is handled outside the privileged Android service whenever possible.
- **Private key material stays opaque to the Android service.** The privileged side works with short-lived opaque key handles instead of receiving raw private-key bytes.
- **Attestation certificate rewriting and signing are performed in Rust.** The production path keeps DER/X.509 manipulation and signing inside the hardened backend boundary.
- **Production no longer relies on the managed Bouncy Castle path for keybox and attestation processing.** Bouncy Castle remains only as a test oracle where needed for differential and regression coverage.
- **The Rust boundary is fail-closed.** Malformed, oversized, mismatched or unsupported material is rejected instead of being partially accepted into the active runtime.

### Hardened keybox pipeline

- **Keybox activation is transactional.** New material becomes active only after the backend has parsed, validated and prepared it successfully.
- **Known-good state is preserved when an update fails.** A broken or incompatible keybox cannot silently replace a working setup.
- **EC, RSA and multi-key keyboxes are validated more deeply** before they are published to the runtime.
- **Key and certificate pairing is proven before use,** and reusable signer state is prepared once instead of repeatedly rebuilding sensitive cryptographic state on hot paths.
- **CBOX and encrypted-backup workflows remain supported** while benefiting from the stricter activation pipeline.

### Attestation engine

- **Attestation handling was rebuilt around the Rust backend** while keeping Android-specific policy decisions on the Android side.
- **Root of Trust, verified-boot data, security patch levels, attestation IDs and module hash handling** now pass through a clearer separation between platform policy and portable certificate rewriting.
- **Generated attestation replacements are cached as encoded certificate data** so repeated key readback can stay lightweight without reparsing or re-signing certificates.
- **Grant and isolated-process behavior preserves the same generated chain** instead of synthesizing a different attestation for each reader.
- **The hot path is designed around real-device behavior and strict regression gates,** with no artificial timing padding or weakened security thresholds.

### Runtime reliability and WebUI

- **Runtime startup, restart and recovery are more resilient.** Components can rebuild their state after failures instead of leaving the module partially active.
- **WebUI runtime communication is more robust,** including health information, resource access and larger request/response handling.
- **Failures are surfaced more clearly** so invalid input, backend activation problems and runtime availability issues are easier to distinguish.
- **Installer and runtime validation are stricter** around missing files, unsafe paths, links, malformed inputs and packaging integrity.

### Companion Encryptor

- **The Encryptor app has been redesigned with a focused monochrome interface** and a cleaner full-screen create/import experience.
- **Vault operations stay local** and retain screen-capture protection, no-backup storage rules and strong encrypted storage.
- **Import, export, overwrite and delete flows are safer and clearer,** with invalid keybox material rejected earlier.
- **File and metadata work has been moved away from the main UI thread** to reduce stalls during larger operations.
- **Language selection is available in-app** for English, Arabic, German, Spanish, Hindi, Indonesian, Russian, Turkish and Simplified Chinese, plus system-default behavior.

### Security engineering

- **Privileged code has a smaller responsibility.** Parsing, cryptographic preparation and certificate construction are pushed out of the privileged service where practical.
- **Sensitive material has a narrower lifetime and exposure surface.** Opaque handles replace raw-key transfer across the Android/Rust boundary.
- **Input and size limits are enforced throughout the pipeline** to reduce parser and resource-exhaustion risk.
- **CI now exercises Rust formatting, clippy, workspace tests, security audit, Android JVM tests, native hardening, fuzz smoke tests, dependency checks and final package validation.**

### Compatibility

- Existing supported **CBOX and encrypted-backup files remain readable**.
- Existing **EC, RSA and multi-key keybox workflows remain supported**.
- **KernelSU and APatch** module packaging remain supported.
- Existing configuration remains familiar while the internals move to the new hardened architecture.

**2.6.0 is the release where CleveresTricky stops being just a feature-focused fork and becomes a security-engineered platform: Rust-first, key-material aware, aggressively validated and built to keep the privileged Android side as small as possible.**
