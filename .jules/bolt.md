# Bolt's Journal

## 2024-05-22 - [Redundant KeyPair Storage]
**Learning:** The `KeyBox` record was storing both the Bouncy Castle `PEMKeyPair` (intermediate) and the Java `KeyPair` (final). This doubled the object overhead for every key loaded.
**Action:** Always check if intermediate parsing objects are being stored in long-lived data structures. Remove them once the final object is created.

## 2024-05-24 - [Redundant IPC Calls in Hot Paths]
**Learning:** `CertHack.createApplicationId` and `Config.getPatchLevel` were making direct IPC calls to `PackageManager` for every key generation/hacking attempt. This is expensive. `Config` already had an LRU cache (`packageCache`) but it was private and underutilized.
**Action:** Expose existing internal caches (safely) to related components (like `CertHack`) instead of re-fetching data via IPC.
