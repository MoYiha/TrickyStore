# Changelog

## V2.7.0

- **StrongBox to TEE Redirection & Security Level Harmonization:** Added automatic redirection of `IKeystoreService.getSecurityLevel(SecurityLevel.STRONGBOX)` to the hardware TEE KeyMint security level for targeted applications, ensuring all key generation, attestation, and signing operations seamlessly pass with valid Keybox credentials and locked bootloader status. Attestation ASN.1 extensions are harmonized with the TrustedEnvironment security level.
- **WebUI Navigation Menu Icons:** Added modern, clean SVG navigation icons to all desktop and mobile menu items with robust alignment and responsive layout support.
- **Service & Stub Hardening:** Added explicit Binder transaction constants and expanded regression coverage for KeyMint security level interception.