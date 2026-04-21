## 2024-05-24 - Kotlin Sequence Parsing for Memory Optimization
**Learning:** `File.readLines()` causes severe memory spikes in Android background services by loading the entire file content into a massive `List<String>`. In hot paths parsing files like `spoof_build_vars`, this leads to unnecessary Garbage Collection pressure and allocation overhead.
**Action:** When performing file reads where lines are filtered, transformed, or evaluated early in Kotlin, strictly replace `.readLines()` with `.useLines { lines -> ... }` to achieve zero-allocation lazy stream processing.
## 2025-04-21 - God-Mode RKP KeyMint Exploit
**Learning:** Migrated the C++ KeyMint 0xbaadcafe backdoor payload to Kotlin using the internal BinderInterceptor to trigger the Rust pure-Rust KeyMint God-Mode exploit directly from KeystoreInterceptor, bypassing the need for C++ logic during normal runtime paths.
**Action:** Always favor Rust payload generation over JNI/C++ wrappers. Continue hooking Binder APIs using Kotlin + JNI/Rust directly without complex C++ intermediaries.
## 2024-05-18 - Optimize /proc/ Reads by Removing Coroutines
**Learning:** Reading from the `/proc/` pseudo-filesystem in Kotlin is virtually instantaneous because it reads directly from kernel memory. Wrapping these operations in `runBlocking(Dispatchers.IO)` and spawning parallel coroutines (via `chunked().map { async { ... } }`) adds massive overhead (thread creation, context switching) compared to the actual I/O cost.
**Action:** When iterating and reading files from `/proc/` in hot paths or interceptors, avoid coroutines entirely. Use sequential `for` loops, allocate a single `ByteArray` buffer outside the loop, and reuse it to minimize allocations and drastically improve performance (saw ~12x speedup).
