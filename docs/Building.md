# Building

**Language:** **English** | [Türkçe](i18n/tr.md#building) | [简体中文](i18n/zh-CN.md#building) | [Español](i18n/es.md#building) | [Deutsch](i18n/de.md#building) | [Русский](i18n/ru.md#building) | [Bahasa Indonesia](i18n/id.md#building) | [हिन्दी](i18n/hi.md#building) | [العربية](i18n/ar.md#building)

## Required tools

The build requires Java 21, the Android SDK for API 36, Android NDK 27.3.13750724, CMake 3.22.1, a stable Rust toolchain, the ARM64 and x86 64 Android Rust targets, and Cargo NDK.

Git submodules must be available because LSPlt provides the Android native hook bridge.

## Validation

Run Kotlin formatting and Android lint from the repository root. Run Rust formatting, clippy with warnings denied, and the full Rust workspace tests from the `rust` directory. The module build also runs unit tests before packaging.

The continuous integration workflow validates shell syntax, shell policy, module template structure, SELinux statement form, Kotlin tests, Java tests, Rust tests, both Android architectures, release ZIP creation, debug ZIP creation, and the Encryptor application. Packaged native files must retain immediate binding, read only relocations, a non executable stack, the Android 64 bit interpreter, and exactly the intended library entry exports.

The source policy rejects every first party C implementation and permits only `binder_interceptor.cpp` as the required first party C++ Android ABI boundary. Required platform headers and the generated Rust interface header are enumerated individually. Portable native additions belong in the Rust workspace.

## Build command

Use `./gradlew zipRelease` for the release module and `./gradlew zipDebug` for a debug module. Gradle installs missing Rust Android targets and Cargo NDK when the Rust toolchain is available.

The build creates the architecture specific Rust `inject` executable and the Rust native core library first. CMake links the native core into the narrowly scoped Android Binder library. Gradle then packages the service, scripts, metadata, policy, `inject`, and `libcleverestricky.so`.

## Artifacts

Module ZIP files are written below `module/release`. The release name includes the project version, commit count, commit identifier, and build type. Each payload receives a SHA 256 record during packaging.

The package task stops when the service APK, an architecture specific Rust `inject` executable, or another required payload is missing. No fake Binder implementation is built. Platform Binder symbols are resolved from the real target process and activation fails safely when its ABI is incompatible.

[Return to the project overview](../README.md)
