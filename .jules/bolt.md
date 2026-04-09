## 2026-06-16 - [Regex Overhead in Hot Paths]
**Learning:** `WebServer.isSafeHost` was using `Regex.matches()` for IPv4/IPv6 validation on every request. This caused `Matcher` allocation and regex engine overhead. Replacing it with manual character loop validation yielded an 8.4x speedup (1258ns -> 149ns).
**Action:** For simple string validation patterns in hot paths, prefer manual loops over `Regex` to avoid allocation and overhead.

## 2026-06-17 - [Redundant Regex Compilation]
**Learning:** WebServer's `serve` method and `validateContent` were compiling `Regex` objects inside loops (for permission and filename validation). This is a classic "compilation in loop" anti-pattern that wastes CPU cycles.
**Action:** Always extract `Regex` patterns to class-level or file-level constants to avoid redundant compilation overhead during parsing or request handling loops.

## $(date +%Y-%m-%d) - [Regex.matches() Allocation Overhead]
**Learning:** `KeyboxVerifier.kt` was using `Regex.matches()` inside `processEntry` to validate hex strings. Because this method is called sequentially for thousands of entries while parsing the Google CRL, the Regex engine overhead and `Matcher` object allocations compounded significantly. By replacing the `HEX_REGEX.matches()` and `WEB_UI_TOKEN_REGEX.matches()` with manual Kotlin `for` loops checking character ranges, we avoided zero-heap allocation inside the CRL parsing tight loop, drastically reducing GC pressure and execution time.
**Action:** In Kotlin hot paths (like parsing large lists or files), avoid using `Regex.matches()` for simple character-class validation (e.g., is-hex, is-alphanumeric). Write manual iteration loops using `str[i]` to ensure zero allocations.
## 2026-04-09 - Lazy Evaluation in Android Binder Interceptors
**Learning:** In high-frequency Android IPC intercepts (e.g. `onPostTransact`), prematurely resolving heavy configuration variables for all potential binder codes causes significant execution overhead. Map lookups and template string resolution add latency before knowing if the target transaction actually needs that data.
**Action:** When creating Binder interceptor patterns with `when (code)` switches, evaluate target replacement properties (like configs) strictly inside their respective code branches, saving map lookups and memory allocations for unmatched (Skip) requests.
