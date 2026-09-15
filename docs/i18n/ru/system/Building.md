# Building

**Язык:** [English](../../../system/Building.md) | [Türkçe](../../tr/system/Building.md) | [简体中文](../../zh-CN/system/Building.md) | [Español](../../es/system/Building.md) | [Deutsch](../../de/system/Building.md) | **Русский** | [Bahasa Indonesia](../../id/system/Building.md) | [हिन्दी](../../hi/system/Building.md) | [العربية](../../ar/system/Building.md)

Нужны Java 21, SDK API 37, NDK 27.3.13750724, CMake 3.22.1, stable Rust, aarch64-linux-android и x86_64-linux-android Android targets, Cargo NDK, submodules. Требуются Kotlin/Android checks, Rust fmt/clippy/tests и unit tests.

CI проверяет shell, SELinux, template, Kotlin/Java/Rust, обе архитектуры, release/debug ZIP и Encryptor. First-party C запрещен; `binder_interceptor.cpp` единственная first-party C++ ABI boundary. Release: `./gradlew zipRelease`.
