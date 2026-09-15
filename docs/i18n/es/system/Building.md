# Building

**Idioma:** [English](../../../system/Building.md) | [Türkçe](../../tr/system/Building.md) | [简体中文](../../zh-CN/system/Building.md) | **Español** | [Deutsch](../../de/system/Building.md) | [Русский](../../ru/system/Building.md) | [Bahasa Indonesia](../../id/system/Building.md) | [हिन्दी](../../hi/system/Building.md) | [العربية](../../ar/system/Building.md)

Se requieren Java 21, SDK API 37, NDK 27.3.13750724, CMake 3.22.1, Rust estable, targets Android aarch64-linux-android y x86_64-linux-android, Cargo NDK y submodules. Deben pasar checks Kotlin/Android, Rust fmt/clippy/tests y unit tests.

CI valida shell, SELinux, template, Kotlin/Java/Rust, ambas arquitecturas, ZIP release/debug y Encryptor. First-party C está prohibido y `binder_interceptor.cpp` es la única frontera C++ permitida. Release se genera con `./gradlew zipRelease`.
