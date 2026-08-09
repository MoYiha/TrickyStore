# Performance and Memory

## Runtime lifecycle

Spoof Engine is the primary resource control. When disabled before boot, native injection is skipped. When disabled at runtime, Binder registrations are removed, injected hooks use one atomic paused check, and scheduled keybox work stops.

Runtime shutdown normally clears all registrations and parks each injected process with one root authorized Binder control transaction. This reduces teardown calls and gives the service an authoritative parked result. A failed cleanup remains pending and is retried at a bounded interval.

Telephony injection runs only when both the master control and Telephony Identity are enabled. Certificate work is limited to eligible Binder calls and selected Android user identifiers.

## Native path

Rust parses Binder streams into a fixed caller owned array. A fixed local buffer and one kernel validated pipe session protect the read path without heap allocation or an unbounded scan. Transaction writeback uses one kernel validated pipe session for all redirected fields instead of opening one pipe for each field. Kernel copies are divided into bounded chunks so a large request cannot wait on pipe capacity.

The Binder descriptor cache uses 64 fixed slots and no heap growth. The Rust hot path validates device plus inode identity before using a cached classification and checks identity again after procfs resolution. The platform weak pointer handoff uses a fixed per thread queue, so transaction bursts cannot grow a dynamic container. A malformed or oversized stream is passed through without unbounded work.

The injector is a short lived Rust process. Rust owns its arguments, logs, file descriptors, buffers, process maps, symbol resolution, ptrace session, register layouts, process memory, socket transfer, loader calls, cleanup, register restoration, and detach state. Temporary target stack writes and the call stack guard use a fixed upper bound. Overlapping ranges are saved once and restored before detach. C++ remains only at the injected Android libbinder and LSPlt boundary.

## Service memory

Package, application rule, DRM, RKP, certificate, patch, template, and keybox caches have fixed entry or byte limits. Policy updates replace state and related caches together. File observers avoid repeated directory scans, while fallback polling uses a low frequency.

The WebUI resource view reads bounded procfs lines only when opened. Its CPU parser avoids regular expressions and token collections, uses a monotonic sampling interval, and cancels an obsolete request when the user leaves or reopens the view.

Encrypted and backup operations enforce expanded size before retaining input. Sensitive temporary byte arrays are cleared where the managed runtime permits.

## Build choices

Release Rust uses full link time optimization, one code generation unit, size optimization, symbol stripping, and caught panic unwinding at FFI boundaries. The small unwind cost prevents an unexpected Rust panic from terminating a critical injected process.

Native outputs use section collection, hidden visibility, stack protection, immediate symbol binding, read only relocation protection, a non executable stack, and position independent execution.

## Lowest overhead setup

Use targeted scope, keep Telephony Identity and early property controls disabled unless needed, retain RKP and DRM passthrough, and disable Spoof Engine before boot when no compatibility behavior is required.

[Return to the project overview](../README.md)
