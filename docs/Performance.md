# Performance and Memory

**Language:** **English** | [Türkçe](i18n/tr.md#performance) | [简体中文](i18n/zh-CN.md#performance) | [Español](i18n/es.md#performance) | [Deutsch](i18n/de.md#performance) | [Русский](i18n/ru.md#performance) | [Bahasa Indonesia](i18n/id.md#performance) | [हिन्दी](i18n/hi.md#performance) | [العربية](i18n/ar.md#performance)

## Runtime lifecycle

Core Keystore interception remains registered while the module service is healthy. The native Binder hook therefore stays available for certificate and TEE compatibility even when Spoof Engine is disabled.

Spoof Engine is the identity resource control. When disabled, optional attestation identity values are not exposed, Telephony Identity is parked when no privacy rule needs it, DRM Identifier Privacy is parked, and optional build and region identity work is skipped. Core certificate handling and boot property protection remain active.

When Spoof Engine is enabled, the DRM privacy controller reconciles modern stable AIDL DRM factories at a bounded interval. It does not busy poll. Lazy or restarted DRM services are rediscovered, while an injector retry for the same process is rate limited.

Automatic Keybox Check has its own control and is independent from Spoof Engine. Disable that worker directly when scheduled revocation work is not wanted.

## Native path

Rust parses Binder streams into a fixed caller owned transaction array without copying the entire Binder response. The parser kernel-validates only command words and transaction payloads that it actually reads; unrelated driver payloads are skipped by their bounded UAPI sizes. Transaction parsing therefore has no stream-sized heap allocation, including responses larger than 16 KiB.

Kernel validated memory copies reuse one pipe per Binder thread instead of opening and closing a pipe for each read or writeback. The pipe is nonblocking, transfers as much as its current capacity permits, and is completely drained after every successful write. Any failed or partial-invalid transfer discards the pipe before a later copy can reuse it. This keeps the invalid-address protection while removing repeated pipe creation and fixed 4 KiB syscall amplification from the hot path.

The Binder descriptor cache uses 64 fixed slots and no heap growth. A positive classification also gets a bounded per-thread fast window, so repeated Binder ioctls avoid a `statx` identity syscall on every call. Device and inode identity are revalidated after at most 31 fast hits, and full procfs resolution still performs a second identity check before caching a new classification. The platform weak pointer handoff uses a fixed per thread queue, so transaction bursts cannot grow a dynamic container. A malformed or oversized stream is passed through without unbounded work.

The injector is a short lived Rust process. Rust owns its arguments, logs, file descriptors, buffers, process maps, symbol resolution, ptrace session, register layouts, process memory, socket transfer, loader calls, cleanup, register restoration, and detach state. Temporary target stack writes and the call stack guard use a fixed upper bound. Overlapping ranges are saved once and restored before detach. C plus plus remains only at the injected Android libbinder and LSPlt boundary.

## DRM privacy cost

DRM Identifier Privacy registers only the stable AIDL `IDrmFactory.createDrmPlugin` and `IDrmPlugin.getPropertyByteArray` transaction codes. Requests for licenses, keys, provisioning, sessions, security level, HDCP state, and DRM string properties never enter the replacement path.

The controller caps tracked DRM factory services at 16 and plugin Binder objects at 256. Dead Binder objects are pruned before new registrations are accepted. Reconciliation runs no more often than the normal runtime controller interval when healthy, and native injection attempts for one PID are rate limited.

A pseudonym is derived only when an isolated application reads exactly `deviceUniqueId`. The derivation reuses the already protected application privacy identity and a thread local SHA 256 instance. There is no persistent DRM ID file and no growing per request or per app DRM pseudonym cache. Output is bounded to 8 through 64 bytes, matching only supported original identifier sizes. Temporary copies of the genuine DRM identifier and the pseudonym are cleared after the replacement Parcel has been constructed.

## Service memory

Package, application rule, DRM, RKP, certificate, patch, template, and keybox caches have fixed entry or byte limits. Policy updates replace state and related caches together. File changes use Android FileObserver and therefore do not wake a periodic polling thread during normal operation. Low frequency polling is enabled only as a fallback when FileObserver cannot be started on the target filesystem.

The WebUI resource view reads bounded procfs lines only when opened. Its CPU parser avoids regular expressions and token collections, uses a monotonic sampling interval, and cancels an obsolete request when the user leaves or reopens the view.

Encrypted and backup operations enforce expanded size before retaining input. Sensitive temporary byte arrays are cleared where the managed runtime permits.

## Measured migration artifacts

Release artifact sizes are recorded from successful GitHub Actions Build artifacts for base commit `f62f8a3b`, the validated Rust-first checkpoint `8a864b61`, and current head `61a900a3`. The current-head artifact is `CleveresTricky-V2.5.8-2684-5647af56-release.zip` from Build run `31966457194`. These numbers measure packaged binary size, not process RSS or PSS.

| Artifact | Base `f62f8a3b` | Checkpoint `8a864b61` | Current head `61a900a3` | Current vs base |
| --- | ---: | ---: | ---: | ---: |
| Release ZIP | 3,954,171 B | 4,805,835 B | 4,939,500 B | +985,329 B (+24.9%) |
| arm64 `inject` | 333,624 B | 333,624 B | 333,624 B | 0 B |
| arm64 `libcleverestricky.so` | 577,512 B | 577,512 B | 577,512 B | 0 B |
| arm64 `webui_bridge` | 327,952 B | 328,216 B | 328,216 B | +264 B |
| arm64 `cleverestrickyd` | not present | 346,512 B | 359,304 B | +359,304 B |
| arm64 `cleverestricky_backend` | not present | 441,288 B | 555,008 B | +555,008 B |
| x86_64 `inject` | 368,768 B | 368,768 B | 368,768 B | 0 B |
| x86_64 `libcleverestricky.so` | 595,248 B | 595,248 B | 595,248 B | 0 B |
| x86_64 `webui_bridge` | 359,944 B | 359,816 B | 359,816 B | -128 B |
| x86_64 `cleverestrickyd` | not present | 379,264 B | 392,688 B | +392,688 B |
| x86_64 `cleverestricky_backend` | not present | 495,568 B | 628,712 B | +628,712 B |

The injected Binder library and injector remain byte-for-byte unchanged in size from the base artifact. The archive growth is concentrated in the new privilege-separated Rust daemon and unprivileged backend plus their packaged support code; this is the deliberate binary-size cost of removing portable privileged/JVM backend logic from the trusted Android adapter path.

Physical-device memory is intentionally not estimated in CI. Capture real idle and exercised process memory on a representative Android device with the same build under test:

```sh
adb shell pidof cleverestrickyd
adb shell pidof cleverestricky_backend
adb shell dumpsys meminfo "$(adb shell pidof cleverestrickyd | tr -d '\r')"
adb shell dumpsys meminfo "$(adb shell pidof cleverestricky_backend | tr -d '\r')"
adb shell sh -c 'for p in $(pidof cleverestrickyd cleverestricky_backend); do echo "== $p =="; grep -E "^(VmRSS|VmHWM):" /proc/$p/status; done'
```

For before/after device comparisons, use the same device, boot state, feature configuration, keybox fixture, workload, and sampling interval. Record the commands, build commit, Android build fingerprint, sample count, and raw PSS/RSS values with the result instead of substituting host-process numbers.

## Build choices

Release Rust uses full link time optimization, one code generation unit, size optimization, symbol stripping, and caught panic unwinding at FFI boundaries. The small unwind cost prevents an unexpected Rust panic from terminating a critical injected process.

Native outputs use section collection, hidden visibility, stack protection, immediate symbol binding, read only relocation protection, a non executable stack, and position independent execution.

## Lowest overhead setup

Keep optional Identity Spoof Engine off when identity substitution and DRM identifier privacy are not needed. Disable Telephony Identity and Automatic Keybox Check unless required. Core Keystore and boot protection remain active because they are the baseline module behavior.

[Return to the project overview](../README.md)

## Optional work scheduling

Optional runtime work follows the resolved feature snapshot. Telephony interception is not retained when no global, active, or assigned profile requires telephony or privacy handling. DRM privacy interception follows the same scoped rule. Identity Refresh does not prepare a next boot snapshot while disabled. Region processing is skipped while disabled. Security Patch returns genuine authorization values without dynamic date resolution while disabled.

Configuration uses immutable state replacement, bounded caches, event driven file observation, and targeted cache invalidation. The legacy periodic keybox file poller is no longer needed because keybox updates use the existing observer path. No new polling loop is introduced.
