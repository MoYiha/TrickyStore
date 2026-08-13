# Changelog

## V2.5.5

- Added a one-click, fixed-allowlist support snapshot that excludes logs, package and keybox names, identity values, credentials, server configuration, and key material.
- Kept scheduled remote keybox maintenance active independently of optional Identity Engine state.
- Reduced residual sensitive data in heap buffers by erasing replaced and released in-memory stream capacity across import, backup, bridge, remote-source, and CBOX paths.
- Pinned every external GitHub Action to a verified immutable commit, including the privileged v3.0.2 release action.

## V2.5.4

- Reduced Binder hot-path syscall and allocation overhead with a reusable kernel-copy pipe, selective Binder stream parsing, and a bounded Binder-FD revalidation cache.
- Switched FilePoller to fallback-only polling when FileObserver is available, and fixed keybox refresh scheduling and repeated log storms.
- Serialized scheduler restarts to avoid overlapping runtime work and improved lifecycle correctness under retries.
- Wiped temporary sensitive buffers after use and tightened temporary data handling.
- Aligned the Android 17/API 37 build toolchain and runtime compatibility paths.

## V2.5.3

- Added granular identity and security patch controls, named profiles, effective state inspection, and safer runtime policy recovery.
- Hardened attestation, KeyMint and StrongBox handling, DRM identifier privacy, upgrade behavior, and Android 17 compatibility.
- Consolidated WebUI ownership into the fixed runtime file set, restored built in local translations, and polished configuration management and mobile navigation.
- Added the compact KeyboxHub remote server helper with external browser routing while keeping WebUI navigation isolated.
- Improved runtime diagnostics, cache and timing behavior, dependency security, regression coverage, and release artifact validation.

Earlier release history is available on [GitHub Releases](https://github.com/tryigit/CleveresTricky/releases).
