# Building

**Bahasa:** [English](../../../system/Building.md) | [Türkçe](../../tr/system/Building.md) | [简体中文](../../zh-CN/system/Building.md) | [Español](../../es/system/Building.md) | [Deutsch](../../de/system/Building.md) | [Русский](../../ru/system/Building.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/system/Building.md) | [العربية](../../ar/system/Building.md)

Memerlukan Java 21, SDK API 37, NDK 27.3.13750724, CMake 3.22.1, stable Rust, target Android aarch64-linux-android dan x86_64-linux-android, Cargo NDK dan submodule. Kotlin/Android checks, Rust fmt/clippy/tests dan unit tests harus lulus.

CI memvalidasi shell, SELinux, template, Kotlin/Java/Rust, dua architecture, release/debug ZIP dan Encryptor. First-party C dilarang; `binder_interceptor.cpp` satu-satunya C++ ABI boundary. Release: `./gradlew zipRelease`.
