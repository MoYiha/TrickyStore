# Native Architecture

**Language:** **English** | [Türkçe](i18n/tr.md#native-architecture) | [简体中文](i18n/zh-CN.md#native-architecture) | [Español](i18n/es.md#native-architecture) | [Deutsch](i18n/de.md#native-architecture) | [Русский](i18n/ru.md#native-architecture) | [Bahasa Indonesia](i18n/id.md#native-architecture) | [हिन्दी](i18n/hi.md#native-architecture) | [العربية](i18n/ar.md#native-architecture)

## Design goal

The project uses Rust for every native component that can be implemented without depending on an unstable Android C++ object ABI. Continuous integration rejects first party C source and rejects every first party C++ implementation except the single Binder boundary described below.

There is no first party C implementation. New portable native logic must be written in Rust.

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

## Boundary verification

Rust and the narrow C++ boundary share explicit layout structures. Tests and compile time assertions pin their size and critical field offsets on supported 64 bit architectures. Both Android targets are compiled and linked in continuous integration.

Release binaries expose only the intended library entry points, use position independent code, immediate binding, read only relocations, a non executable stack, stack protection, hidden visibility, and dead section removal.

## Resource behavior

The injected Binder parser performs no heap allocation. Descriptor classification uses a fixed cache and validates device plus inode identity before trusting a cached result. A second identity check closes the file descriptor reuse race around procfs resolution. Rust release code uses size optimization and full link time optimization. The injector exits after activation, so its orchestration buffers do not remain in the target process.

[Return to the project overview](../README.md)
