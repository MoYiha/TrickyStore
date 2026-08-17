# Native Architecture

**Language:** **English** | [Türkçe](i18n/tr.md#native-architecture) | [简体中文](i18n/zh-CN.md#native-architecture) | [Español](i18n/es.md#native-architecture) | [Deutsch](i18n/de.md#native-architecture) | [Русский](i18n/ru.md#native-architecture) | [Bahasa Indonesia](i18n/id.md#native-architecture) | [हिन्दी](i18n/hi.md#native-architecture) | [العربية](i18n/ar.md#native-architecture)

## Design goal

The project uses Rust for every native component that can be implemented without depending on an unstable Android C++ object ABI. Continuous integration rejects first party C source and rejects every first party C++ implementation except the single Binder boundary described below.

There is no first party C implementation. New portable native logic must be written in Rust.

## Rust service and privilege boundary

The long-lived module service is split into a small privileged Rust supervisor and an unprivileged Rust backend. The privileged daemon owns process supervision, the fixed Android adapter identity, bounded CTIP framing, and descriptor-relative access to the fixed module configuration root. It does not parse keybox XML, CBOX JSON, certificates, private keys, backup plaintext, ZIP payloads, or network content.

The daemon opens the trusted configuration-root capability once during startup, before Android adapter/backend work begins. Config writers and the keybox FD broker share that same live `TrustedDir` capability, including across supervised backend restarts; the configuration-root pathname is not reopened later. Sensitive keybox files are opened relative to that descriptor with `openat`, `O_NOFOLLOW`, `O_CLOEXEC`, file-type validation, and explicit byte limits. The root daemon snapshots the validated input into a sealed memfd and passes exactly one descriptor over the private inherited Unix socket using `SCM_RIGHTS`. Ancillary messages are close-on-exec at receive time; truncated, ambiguous, or multi-descriptor messages fail closed and installed descriptors are closed before the error returns.

The backend permanently drops supplementary groups and switches to Android's nobody UID/GID before accepting parser work. It enables `PR_SET_NO_NEW_PRIVS`, disables dumpability, restores a parent-death signal after the credential transition, applies resource limits, and runs from `/`. It revalidates received keybox descriptors, performs bounded reads, parses XML, verifies private-key/leaf consistency, normalizes secret keys to PKCS#8, and keeps that material inside a bounded zeroizing Rust key store. Keybox wire v3 returns only opaque 16-byte key IDs plus bounded public certificate material; private-key bytes are not returned to the managed process. CBOX decrypt/verify/key registration and backup cryptography also run in this backend.

The Android process is only an adapter for platform APIs that Rust cannot directly replace safely. It connects to the backend over an abstract Unix-domain socket whose peer PID/UID and executable identity are verified. Managed keybox objects retain an opaque backend key handle rather than PKCS#8 or provider `PrivateKey` material. The remaining managed certificate adapter materializes public `X509Certificate` objects and Android/Binder-facing framework state; certificate rewrite/signing requests refer to the opaque key ID and stay in Rust. The portable XML/DER parser and secret-key store are not in this adapter.

WebUI request polling files have been removed from the control path. The native WebUI bridge and Android HTTP adapter use bounded CTIP frames over an abstract Unix-domain socket, with fixed-size streaming relay buffers for request/response forwarding. Large WebUI body staging remains a separate bounded compatibility path rather than expanding the privileged control-frame parser.

## Rust native core

The native core validates Binder transaction and exchange layouts, parses Binder command streams, classifies live Binder file descriptors, parses Android and kernel versions, validates live probes, performs kernel validated Binder memory copies, checks injector requests, reads bounded target process names, parses control messages, generates secure socket names, and validates library metadata.

The parser writes into a fixed caller supplied transaction array. Binder response bytes first pass through a bounded kernel validated copy, so a raced or unreadable address cannot become a Rust slice. Every size, count, offset, addition, pointer range, command payload, and output capacity is checked before use. Unknown Binder layouts fail closed.

## Rust injector core

The injector core owns its executable entry, argument parsing, Android logging, path canonicalization, open file validation, file descriptor lifetime, SELinux socket context handling, random abstract socket naming, ancillary descriptor transfer, process map parsing, local and remote symbol resolution, ptrace session control, architecture register layouts, calling conventions, process memory access, remote allocation tracking, dynamic loader calls, error collection, cleanup, register restoration, and detach behavior.

Every temporary stack write and a fixed call stack guard are recorded in a bounded memory journal and restored in reverse order before register restoration. Overlapping ranges reuse the original saved copy, which keeps memory bounded across repeated loader calls. Remote calls return through a validated non executable mapping, descriptor messages must contain exactly one descriptor, and a library that rejects activation is closed before detach. These rules prevent persistent stack corruption and reduce leaked state in critical Android processes.

The architecture specific `inject` executables are built directly from Rust. There is no first party C or C++ launcher and no injector bridge in another language.

## Required Android C++ boundary

Only `binder_interceptor.cpp` remains as first party C++ implementation source. All project C++ definitions are contained in that one translation unit. It constructs and observes the platform `BBinder`, `Parcel`, strong pointer, weak pointer, and IPC thread state objects already used by the target process. Its fixed transaction queue retains those nontrivial weak pointer objects without dynamic queue growth. The boundary also installs the LSPlt hook and provides the variadic `ioctl` interposition point required by the Binder driver path.

These operations cannot currently use the Android NDK Binder C interface. The target process uses the private platform libbinder object model, and the interception path needs its exact parcel representation and lifetime behavior. Replacing this boundary with guessed Rust layouts or an unrelated NDK Binder object would make the injected system process less safe.

The vendored LSPlt implementation remains C++ as an external dependency. Its source is kept isolated below the external directory and is not treated as project implementation code. Retained platform headers contain private ABI declarations and required inline libbinder ownership helpers. The generated Rust interface header declares the narrow C ABI used by the bridge. Fake Binder and utils implementations are not built or packaged. Runtime symbols resolve from the real Android platform libraries already loaded by the target process.

This exception must not grow. Any part that becomes practical to express safely in Rust must move to Rust, and a new first party C or C++ source file fails the source policy check.

## Camera visibility boundary

Camera visibility is an explicit opt-in runtime path. A cold-disabled configuration does not scan for `cameraserver`, invoke the injector, or register the camera Binder interceptor. When enabled, the injector target remains restricted to the exact `cameraserver` process name.

Android SELinux policy is loaded statically, so the `cameraserver` `execmem` permission required by the existing LSPlt/Binder injection mechanism cannot follow the WebUI toggle dynamically. Installing the module therefore makes that narrow permission available even while Camera visibility is disabled. This is an explicit attack-surface trade-off of the current injection design; removing it requires replacing the cameraserver hook mechanism rather than changing the runtime feature gate.

Listener shutdown deliberately does not remove a proxy and re-register the application's original listener from the module service. Such a re-registration would originate under the module service identity instead of the application's original Binder caller identity and could change CameraService permission and device-context filtering. Instead, disabling the feature restores hidden camera states to already-registered application listeners and switches their existing proxies to pass-through drain mode. New listeners are not proxied while disabled. The narrow interceptor remains only until those pre-existing listeners are removed, die, or CameraService restarts, after which the hook is parked. This transition adds no polling worker and avoids breaking a live `CameraManagerGlobal` connection.

## Boundary verification

Rust and the narrow C++ boundary share explicit layout structures. Tests and compile time assertions pin their size and critical field offsets on supported 64 bit architectures. Both Android targets are compiled and linked in continuous integration.

Release binaries expose only the intended library entry points, use position independent code, immediate binding, read only relocations, a non executable stack, stack protection, hidden visibility, and dead section removal.

## Resource behavior

The injected Binder parser performs no heap allocation. Descriptor classification uses a fixed cache and validates device plus inode identity before trusting a cached result. A second identity check closes the file descriptor reuse race around procfs resolution. Rust release code uses size optimization and full link time optimization. The injector exits after activation, so its orchestration buffers do not remain in the target process.

The service control protocol has explicit operation-specific payload caps. Keybox files and XML are limited to 10 MiB; the keybox wire also has a structural overhead bound. Backup and CBOX operations keep their own compatibility bounds and wipe mutable secret buffers after use. The backend accepts one connection at a time from the exact supervised Android adapter PID rather than creating an unbounded worker pool.

Camera listener state is also bounded. Initial callbacks received before the listener snapshot is known are retained in a fixed-count, fixed-byte queue, replay state is capped, and the listener proxy map has a hard limit. Exceeding the proxy limit while filtering is active fails closed instead of registering an unfiltered listener.

[Return to the project overview](../README.md)
