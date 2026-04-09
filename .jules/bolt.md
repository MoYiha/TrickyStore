## 2024-05-18 - Avoid runBlocking Overhead in Kotlin Loops
**Learning:** Placing `runBlocking(Dispatchers.IO)` inside a loop (like iterating through chunks) creates the heavy Coroutine dispatcher and event loop infrastructure on every single iteration, severely degrading performance.
**Action:** When parallelizing I/O across chunks with coroutines, always wrap the *entire* loop inside a single `runBlocking` block, and use `async/awaitAll` on the individual chunks within that block to eliminate redundant infrastructure setup overhead.
