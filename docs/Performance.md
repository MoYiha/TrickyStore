# Performance and Memory

## Runtime lifecycle

Core Keystore interception remains registered while the module service is healthy. The native Binder hook therefore stays available for certificate and TEE compatibility even when Spoof Engine is disabled.

Spoof Engine is the identity resource control. When disabled, optional attestation identity values are not exposed, Telephony Identity is parked when no privacy rule needs it, and optional build and region identity work is skipped. Core certificate handling and boot property protection remain active.

Automatic Keybox Check has its own control and is independent from Spoof Engine. Disable that worker directly when scheduled revocation work is not wanted.

## Native path

Rust parses Binder streams into a fixed caller owned array. A fixed local buffer and one kernel validated pipe session protect the read path without heap allocation or an unbounded scan. Transaction writeback uses one kernel validated pipe session for all redirected fields instead of opening one pipe for each field. Kernel copies are divided into bounded chunks so a large request cannot wait on pipe capacity.

The Binder descriptor cache uses 64 fixed slots and no heap growth. The Rust hot path validates device plus inode identity before using a cached classification and checks identity again after procfs resolution. The platform weak pointer handoff uses a fixed per thread queue, so transaction bursts cannot grow a dynamic container. A malformed or oversized stream is passed through without unbounded work.

The injector is a short lived Rust process. Rust owns its arguments, logs, file descriptors, buffers, process maps, symbol resolution, ptrace session, register layouts, process memory, socket transfer, loader calls, cleanup, register restoration, and detach state. Temporary target stack writes and the call stack guard use a fixed upper bound. Overlapping ranges are saved once and restored before detach. C plus plus remains only at the injected Android libbinder and LSPlt boundary.

## Service memory

Package, application rule, DRM, RKP, certificate, patch, template, and keybox caches have fixed entry or byte limits. Policy updates replace state and related caches together. File changes use Android FileObserver and therefore do not wake a periodic polling thread during normal operation. Low frequency polling is enabled only as a fallback when FileObserver cannot be started on the target filesystem.

The WebUI resource view reads bounded procfs lines only when opened. Its CPU parser avoids regular expressions and token collections, uses a monotonic sampling interval, and cancels an obsolete request when the user leaves or reopens the view.

Encrypted and backup operations enforce expanded size before retaining input. Sensitive temporary byte arrays are cleared where the managed runtime permits.

## Build choices

Release Rust uses full link time optimization, one code generation unit, size optimization, symbol stripping, and caught panic unwinding at FFI boundaries. The small unwind cost prevents an unexpected Rust panic from terminating a critical injected process.

Native outputs use section collection, hidden visibility, stack protection, immediate symbol binding, read only relocation protection, a non executable stack, and position independent execution.

## Lowest overhead setup

Keep optional Identity Spoof Engine off when identity substitution is not needed. Disable Telephony Identity and Automatic Keybox Check unless required. Core Keystore and boot protection remain active because they are the baseline module behavior.

[Return to the project overview](../README.md)