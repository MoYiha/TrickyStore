## 2024-05-24 - Kotlin Sequence Parsing for Memory Optimization
**Learning:** `File.readLines()` causes severe memory spikes in Android background services by loading the entire file content into a massive `List<String>`. In hot paths parsing files like `spoof_build_vars`, this leads to unnecessary Garbage Collection pressure and allocation overhead.
**Action:** When performing file reads where lines are filtered, transformed, or evaluated early in Kotlin, strictly replace `.readLines()` with `.useLines { lines -> ... }` to achieve zero-allocation lazy stream processing.
## 2025-04-21 - God-Mode RKP KeyMint Exploit
**Learning:** Migrated the C++ KeyMint 0xbaadcafe backdoor payload to Kotlin using the internal BinderInterceptor to trigger the Rust pure-Rust KeyMint God-Mode exploit directly from KeystoreInterceptor, bypassing the need for C++ logic during normal runtime paths.
**Action:** Always favor Rust payload generation over JNI/C++ wrappers. Continue hooking Binder APIs using Kotlin + JNI/Rust directly without complex C++ intermediaries.
## 2025-04-21 - [CopyOnWriteArrayList vs ConcurrentHashMap for Lookups]
**Learning:** O(N) list searches on `CopyOnWriteArrayList` in Kotlin can be exceptionally slow compared to O(1) lookups using `ConcurrentHashMap`. In this codebase, searching for a string ID took ~1.5 seconds for 10,000 iterations over a small list vs 1ms for map lookups.
**Action:** When a collection is frequently searched by a unique ID but also iterated, maintain both a `CopyOnWriteArrayList` for ordered iteration and a `ConcurrentHashMap` for O(1) lookups to avoid O(N) linear search bottlenecks.
