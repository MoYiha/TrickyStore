# Rust-first migration baseline

This document freezes the production behavior that the Rust service migration must preserve. It is intentionally written before switching any production backend path.

## Current production architecture

```text
KernelSU/APatch WebUI
  -> rust/webui-bridge (command-per-request)
  -> bounded files + JSON/Base64 polling
  -> JVM WebUiBridge
  -> JVM WebServer / Config / PolicyState / keybox / CBOX / backup
  -> JVM CertHack + Bouncy Castle
  -> Android framework adapters
  -> Rust injector
  -> keystore2
  -> tiny C++ private-libbinder ABI island
  -> Rust native-core
```

The migration target is a long-lived Rust daemon with Unix-domain-socket IPC. The JVM is retained only where Android framework objects or private platform APIs cannot be reached reliably without an Android runtime adapter. The private libbinder ABI remains in the existing C++ island; Rust must not reproduce BBinder, Parcel, RefBase, sp/wp or vtable layouts.

## Behavior that is already security-sensitive

### Binder / keystore hot path

- Core keystore interception is always-on while the service is healthy.
- Binder parsing is bounded to the supported Android UAPI layouts and fails closed on malformed streams.
- The Rust parser writes into caller-owned bounded storage and does not copy the complete Binder response.
- Binder FD classification remains bounded and cached; no filesystem, network, XML, JSON, config reload, daemon IPC or JVM allocation may be introduced per Binder transaction.
- The C ABI catches Rust panics before returning to C++.
- The C++ translation unit is an ABI island only and must not grow to absorb portable logic.

### CBOX compatibility contract

- Magic: `CBOX`.
- Version: big-endian u32, currently 1 or 2.
- Salt: 16 bytes.
- IV: 12 bytes.
- KDF: PBKDF2-HMAC-SHA256, 250000 iterations, 256-bit output.
- AEAD: AES-256-GCM with a 16-byte tag.
- Version 2 authenticates `magic || version || salt || iv` as AAD; version 1 is read-compatible without AAD.
- Ciphertext is bounded to 10 MiB.
- Decrypted payload fields are `author`, `xml_content`, `signature`, and `signature_version`.
- Signature version 1 signs UTF-8 `author || xml_content`.
- Signature version 2 signs `CBOX-SIGNATURE-V2\0 || be32(author_len) || author || be32(xml_len) || xml`.
- Signature algorithms are SHA-256 with RSA or ECDSA according to the SPKI key type.
- Password, key material and plaintext scratch buffers are wiped on all paths where the runtime permits.

### Encrypted backup compatibility contract

- Magic: `CTSB`.
- Version: big-endian u32; v1 remains readable and v2 is the current writer.
- Salt: 16 bytes; IV: 12 bytes; GCM tag: 16 bytes.
- KDF: PBKDF2-HMAC-SHA256, 250000 iterations, 256-bit output.
- AEAD: AES-256-GCM.
- Version 2 authenticates the complete 36-byte header as AAD; version 1 does not.
- Plaintext backups are bounded to 32 MiB.

### Keybox / X.509 compatibility contract

- Root element is `AndroidAttestation` and the declared keybox count must exactly match parsed entries.
- At most 64 keyboxes per file, 4 keys per keybox, 16 certificates per chain and 256 KiB per PEM field.
- RSA and EC keyboxes are accepted only when the private key proves possession of the leaf public key and every certificate in the chain is currently valid and verifies against the next issuer.
- The Android attestation extension OID is `1.3.6.1.4.1.11129.2.1.17`.
- Authorization-list parsing must preserve unknown tags and fail closed on conflicting patch-level tags.
- RootOfTrust is tag 704; system/vendor/boot patch levels are 706/718/719; module hash is 724; attestation IDs are 710, 711, 712, 713, 714, 715, 716, 717 and 723.
- Device-ID overrides are applied only to IDs already present in the genuine attestation record.
- Patch replacements preserve whether a tag originally lived in TEE or software authorization lists.
- Module hash handling is enabled only for attestation and KeyMint versions >= 400.
- Rewritten leaves preserve serial, validity, subject, subject public key and all non-attestation extensions, then are signed by a verified keybox CA.
- Generated chains are cached by exact genuine leaf DER with a bounded 64-entry LRU-equivalent policy.

## Filesystem and IPC defects to remove

The current managed secure-file helper rejects symlinks before later path-based operations. That is useful validation but is not a race-free security boundary. Production Rust filesystem code must use trusted directory descriptors plus `openat`/`renameat`, `O_NOFOLLOW`, `O_CLOEXEC`, `fstat`, data sync and directory sync. `openat2` hardening may be used when available with a compatible fallback.

The current native WebUI transport writes request/response files, encodes inline bodies with Base64 and relies on FileObserver plus fallback scans. The replacement transport must be a bounded Unix-domain socket protocol with deadlines, partial read/write handling and fixed maximum frames. Large payloads should use file descriptors instead of Base64 copies when the Android SELinux domain permits it.

## Baseline measurement protocol

Device PSS/RSS and Binder timing cannot be measured faithfully on a GitHub-hosted runner and must never be fabricated. Before production cutover, capture on the same physical device/build for old and new binaries:

```sh
adb shell su -c 'dumpsys meminfo $(pidof -s CleveresTricky)'
adb shell su -c 'cat /proc/$(pidof -s CleveresTricky)/status | grep -E "VmRSS|VmHWM|Threads"'
adb shell su -c 'cat /proc/$(pidof -s CleveresTricky)/smaps_rollup | grep -E "Rss:|Pss:|Private_"'
adb shell su -c 'ls -l /proc/$(pidof -s CleveresTricky)/fd | wc -l'
```

For native artifact size, compare stripped arm64-v8a and x86_64 `inject`, `webui_bridge`, `cleverestrickyd`, the C++ bridge `.so`, and `service.apk`. Binder timing must use the repository's existing TEE/attestation regression procedure and preserve the AGENTS.md `< 1.1x` threshold.

## Cutover gates

A backend replacement is allowed only after its compatibility tests pass against the current implementation, hostile/truncated/oversized inputs fail closed, and the new path has no unbounded queues or parser allocation. Certificate production cutover additionally requires semantic differential tests for RSA and EC chains, malformed DER, Android attestation records, RootOfTrust, patch levels, attestation IDs, signatures and chain verification. Byte-identical ECDSA output is not required; decoded security semantics and cryptographic verification are.

No migration is permitted to change `update.json` or add WebUI runtime files outside the fixed inventory in `AGENTS.md`.
