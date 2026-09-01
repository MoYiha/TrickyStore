# Changelog

## V2.7.2

- **DRM & Multi-User Support:** Improved DRM privacy handling across secondary Android users, associated DRM plugin registrations with the originating application UID, and resolved live application UIDs reliably from kernel process state.
- **Security & Reliability:** Fixed large decimal serial parsing in `KeyboxVerifier`, hardened backup/restore filesystem handling, improved security-patch date parsing, added safer manual `action.sh` execution warnings, and strengthened runtime/process handling.
- **Performance:** Optimized identity override lookups with sorted binary search and reduced intermediate allocations in file filtering paths.
- **WebUI & Localization:** Added complete server-list translations across supported languages, automatic Android system-locale detection with browser fallback, dynamic retranslation of generated controls, improved table header layout, and a more readable 16px base font.
- **Build & Compatibility:** Updated Kotlin and remap dependencies, improved BouncyCastle/CodeQL build configuration, added the `IUserManager` AIDL stub integration, and corrected service remapping configuration.
- **Native Core Maintenance:** Refined Rust attestation/CRL components and related service/native code paths while preserving the existing runtime payload contracts.
