# Building

**भाषा:** [English](../../../system/Building.md) | [Türkçe](../../tr/system/Building.md) | [简体中文](../../zh-CN/system/Building.md) | [Español](../../es/system/Building.md) | [Deutsch](../../de/system/Building.md) | [Русский](../../ru/system/Building.md) | [Bahasa Indonesia](../../id/system/Building.md) | **हिन्दी** | [العربية](../../ar/system/Building.md)

Java 21, SDK API 37, NDK 27.3.13750724, CMake 3.22.1, stable Rust, aarch64-linux-android और x86_64-linux-android Android targets, Cargo NDK और submodules चाहिए। Kotlin/Android checks, Rust fmt/clippy/tests और unit tests pass होने चाहिए।

CI shell, SELinux, template, Kotlin/Java/Rust, दोनों architectures, release/debug ZIP और Encryptor verify करता है। First-party C prohibited है; `binder_interceptor.cpp` केवल C++ ABI exception है। Release: `./gradlew zipRelease`।
