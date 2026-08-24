🧪 [testing improvement] Add test coverage for KeyboxVerifier.countRevokedKeys()

🎯 **What:** The untested utility function `countRevokedKeys()` in `KeyboxVerifier.kt` lacked test coverage.
📊 **Coverage:** Covered the happy path (returning `normalizedEntryCount` from `fetchCrl()`) and the failure/empty path (returning `-1` when `fetchCrl()` returns null).
✨ **Result:** Improved test coverage for `KeyboxVerifier` and ensures the revoked keys count functionality won't silently break in future refactoring.
