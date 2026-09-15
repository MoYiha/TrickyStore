# Building

**Sprache:** [English](../../../system/Building.md) | [Türkçe](../../tr/system/Building.md) | [简体中文](../../zh-CN/system/Building.md) | [Español](../../es/system/Building.md) | **Deutsch** | [Русский](../../ru/system/Building.md) | [Bahasa Indonesia](../../id/system/Building.md) | [हिन्दी](../../hi/system/Building.md) | [العربية](../../ar/system/Building.md)

Benötigt Java 21, SDK API 37, NDK 27.3.13750724, CMake 3.22.1, stable Rust, aarch64-linux-android und x86_64-linux-android Android targets, Cargo NDK und Submodules. Kotlin/Android-Checks, Rust fmt/clippy/tests und Unit Tests müssen erfolgreich sein.

CI prüft Shell, SELinux, Template, Kotlin/Java/Rust, beide Architekturen, Release/Debug ZIP und Encryptor. First-party C ist verboten; `binder_interceptor.cpp` ist die einzige erlaubte first-party C++ ABI-Grenze. Release: `./gradlew zipRelease`.
