## 2024-05-24 - Kotlin Sequence Parsing for Memory Optimization
**Learning:** `File.readLines()` causes severe memory spikes in Android background services by loading the entire file content into a massive `List<String>`. In hot paths parsing files like `spoof_build_vars`, this leads to unnecessary Garbage Collection pressure and allocation overhead.
**Action:** When performing file reads where lines are filtered, transformed, or evaluated early in Kotlin, strictly replace `.readLines()` with `.useLines { lines -> ... }` to achieve zero-allocation lazy stream processing.
