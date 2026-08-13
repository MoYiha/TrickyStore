# Changelog

## V2.5.6

- Restored native activation on KernelSU/mount-namespace devices with fail-closed platform image and ELF identity validation.
- Added stage-specific native activation diagnostics and strengthened TEE/native regression guardrails.
- Completed all built-in WebUI locales with full first-party catalog coverage and localization regression tests.
- Published the verified V2.5.6 module and Encryptor app release artifacts with SHA256 checksums and provenance.

## V2.5.5

- Added a one-click, fixed-allowlist support snapshot that excludes logs, package and keybox names, identity values, credentials, server configuration, and key material.
- Kept scheduled remote keybox maintenance active independently of optional Identity Engine state.
- Reduced residual sensitive data in heap buffers by erasing replaced and released in-memory stream capacity across import, backup, bridge, remote-source, and CBOX paths.
- Pinned every external GitHub Action to a verified immutable commit, including the privileged v3.0.2 release action.

## V2.5.3

- Added granular identity and security patch controls, named profiles, effective state inspection, and safer runtime policy recovery.
- Hardened attestation, KeyMint and StrongBox handling, DRM identifier privacy, upgrade behavior, and Android 17 compatibility.
- Consolidated WebUI ownership into the fixed runtime file set, restored built in local translations, and polished configuration management and mobile navigation.
- Added the compact KeyboxHub remote server helper with external browser routing while keeping WebUI navigation isolated.
- Improved runtime diagnostics, cache and timing behavior, dependency security, regression coverage, and release artifact validation.

Earlier release history is available on [GitHub Releases](https://github.com/tryigit/CleveresTricky/releases).
