# CleveresTricky Detection Analysis & Mitigation

This document provides a comprehensive analysis of theoretical detection vectors that can identify TEE emulators like CleveresTricky/TrickyStore, and details the defensive strategies employed in this repository to mitigate them.

## 1. Runtime Trace Analysis

**Threat:** Advanced security solutions (e.g., DroidGuard, banking apps) may use eBPF or kernel tracing to monitor system calls and IPC traffic.

*   **Vector 1: `ioctl` Pattern Matching**
    *   *Detection:* Monitoring `/dev/binder` `ioctl` calls for unusual transaction patterns or modified payloads.
    *   *Mitigation:* **Direct `ioctl` Hooking (Rust)**. We use a custom `ioctl` interceptor that modifies transactions *before* they reach the kernel driver. The modification happens in userspace memory, so kernel-level traces only see the "spoofed" data, provided the hook is placed deep enough.
    *   *Risk:* High. If the hook is detected or bypassed, the original transaction is visible.

*   **Vector 2: `binder_transaction` Tracing**
    *   *Detection:* eBPF programs attaching to the `binder_transaction` kprobe can inspect the `struct binder_transaction_data`.
    *   *Mitigation:* **Userspace Sanitization**. By modifying the Parcel data in the sending process (Zygisk injection), the data is already "fake" when it enters the kernel. We ensure our hooks run in the context of the calling app.

*   **Vector 3: `ptrace` Detection**
    *   *Detection:* Checking `TracerPid` in `/proc/self/status` or using `ptrace(PTRACE_TRACEME)` to see if a debugger/hook is attached.
    *   *Mitigation:* **Anti-Debugging Daemon**. Our native daemon proactively checks for `TracerPid` and `PTRACE_TRACEME` availability. We also employ "Self-Debugging" where a parent process traces the child to prevent external attachment (a common anti-tamper technique).

## 2. Timing Analysis (Side-Channel)

**Threat:** TEE operations (e.g., hardware-backed signing) have distinct timing characteristics compared to software emulations.

*   **Vector 4: RSA/ECDSA Signing Latency**
    *   *Detection:* Hardware-backed keys (StrongBox/TEE) are significantly *slower* than software implementations (OpenSSL/BoringSSL) due to secure element communication overhead.
    *   *Mitigation:* **Artificial Delays**. The `RaceManager` (and future crypto wrappers) can inject random or fixed latency (e.g., 5-50ms) to match the statistical profile of a real TEE.
    *   *Status:* **Race Condition Engine** is implemented to disrupt precise timing measurements by introducing scheduler noise on the target core.

*   **Vector 5: Cache Timing Attacks**
    *   *Detection:* Monitoring L3 cache hits/misses to infer cryptographic key usage.
    *   *Mitigation:* **Constant-Time Algorithms**. We rely on Rust's `p256` and `hmac` crates which are designed to be constant-time, preventing simple timing side-channels.

## 3. Memory Artifacts

**Threat:** The presence of the emulator code itself in the process memory.

*   **Vector 6: Executable Anonymous Memory (RWX)**
    *   *Detection:* Scanning `/proc/self/maps` for memory regions that are both Writable and Executable (RWX), which is typical for JIT compilers or dynamic hooks (like Frida/Zygisk).
    *   *Mitigation:* **ELF Header Unlinking**. The native daemon and injected libraries attempt to "sanitize" memory maps by unlinking or modifying their headers to appear as anonymous data or standard read-only mappings.
    *   *Ref:* `sanitize_memory_maps()` in `daemon/main.cpp`.

*   **Vector 7: String Analysis**
    *   *Detection:* Scanning memory for strings like "CleveresTricky", "Zygisk", "Hook", or "Spoof".
    *   *Mitigation:* **String Obfuscation**. Sensitive strings should be XOR-encoded or constructed at runtime. (Partially implemented; future work required).

*   **Vector 8: Library Signatures**
    *   *Detection:* Checking for the presence of `libcleverestricky_cbor_cose.so` or `libbinder_interceptor.so`.
    *   *Mitigation:* **Hidden Visibility**. We compile with `-fvisibility=hidden` and strip all symbols. The library name itself is often randomized or hidden by the injector (Zygisk).

## 4. File System Artifacts

**Threat:** Detection of files on disk.

*   **Vector 9: Magisk/SU Binaries**
    *   *Detection:* Checking for `/sbin/su`, `/data/adb`, or module directories.
    *   *Mitigation:* **Mount Namespace Separation**. Magisk/Zygisk handles this by unmounting these directories for isolated processes. Our module relies on this standard concealment.

*   **Vector 10: Configuration Files**
    *   *Detection:* Reading `/data/adb/cleverestricky/`.
    *   *Mitigation:* **Strict Permissions**. Ensure config files are `600` (root only). The daemon reads them once and caches config in memory (`Config.kt`), minimizing file system access windows.

## 5. Behavioral Analysis

**Threat:** Detecting the *act* of hooking, rather than the hook itself.

*   **Vector 11: Prologue Inspection (CRC Checks)**
    *   *Detection:* Reading the first few bytes of sensitive functions (e.g., `ioctl`, `__system_property_get`) to see if they are a `JMP` instruction (inline hook).
    *   *Mitigation:* **PLT/GOT Hooking**. We primarily use PLT (Procedure Linkage Table) hooking via `lsplt`. This modifies the *pointer* in the GOT, not the code itself. This bypasses inline hook detectors that only scan the `.text` section.

*   **Vector 12: Stack Unwinding**
    *   *Detection:* Checking the call stack during a sensitive operation. If `libcleverestricky.so` appears in the backtrace, it's a flag.
    *   *Mitigation:* **Tail Call Optimization & ROP**. Advanced hooks can use tail calls to avoid pushing a frame, or manipulate the stack pointer to hide their presence. (Complex, considered for v2.0).

## 6. Defensive Strategy Summary

| Category | Technique | Implementation |
| :--- | :--- | :--- |
| **Stealth** | Process Renaming | `prctl(PR_SET_NAME, "kworker/u0:0-events")` |
| **Stealth** | Anti-Debugging | `ptrace(PTRACE_TRACEME)` & `TracerPid` check |
| **Integrity** | Binder Hooking | `lsplt` (PLT Hook) on `ioctl` & `close` |
| **Crypto** | Memory Safety | Rust FFI (`cbor-cose`) with `Zeroize` traits |
| **Stability** | FD Management | Cache invalidation in `binder_interceptor.cpp` |
| **Evasion** | Race Conditions | `RaceManager` (Thread Pinning + Barrier Sync) |

## 7. Known Limitations

*   **Kernel-Level Deep Packet Inspection:** If a customized kernel is used (e.g., by a very aggressive anti-cheat), userspace hooks are visible.
*   **Hardware Attestation (Remote):** We cannot spoof the root hardware key. We rely on finding *other* valid chains (keybox) or exploiting verification logic (the Race Condition/TOCTOU attack).
