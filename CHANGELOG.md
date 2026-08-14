# Changelog

## V2.5.8

- Fixed Restore Defaults enabling Automatic Security Patch on current ROMs when only vendor or boot patch metadata lagged behind.
- Recommended defaults now use the primary system security patch to decide whether Security Patch should be enabled, while system/vendor/boot remain automatic when the feature is actually needed.
- Stabilized early-boot attestation by reusing only fresh, successfully parsed Google attestation revocation state across daemon restarts and reboots.
- Kept Global Mode and Automatic Keybox Check enabled by default while optional identity/privacy features remain disabled.
