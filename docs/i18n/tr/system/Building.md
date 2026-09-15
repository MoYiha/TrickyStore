# Building

**Dil:** [English](../../../system/Building.md) | **Türkçe** | [简体中文](../../zh-CN/system/Building.md) | [Español](../../es/system/Building.md) | [Deutsch](../../de/system/Building.md) | [Русский](../../ru/system/Building.md) | [Bahasa Indonesia](../../id/system/Building.md) | [हिन्दी](../../hi/system/Building.md) | [العربية](../../ar/system/Building.md)

Build için Java 21, Android SDK API 37, NDK 27.3.13750724, CMake 3.22.1, stable Rust, aarch64-linux-android ve x86_64-linux-android Android Rust target'ları, Cargo NDK ve git submodule'ları gerekir. Kotlin/Android lint ile Rust fmt, clippy ve testleri çalıştırılır; modül paketlemesi unit testleri de içerir.

CI shell, SELinux, template structure, Kotlin/Java/Rust testleri, iki mimari, release/debug ZIP ve Encryptor app'i doğrular. First-party C yasaktır; yalnız Android libbinder/LSPlt ABI sınırı olan `binder_interceptor.cpp` first-party C++ istisnasıdır. Release için `./gradlew zipRelease`, debug için `./gradlew zipDebug` kullanılır.
