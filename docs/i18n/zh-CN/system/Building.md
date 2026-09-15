# Building

**语言:** [English](../../../system/Building.md) | [Türkçe](../../tr/system/Building.md) | **简体中文** | [Español](../../es/system/Building.md) | [Deutsch](../../de/system/Building.md) | [Русский](../../ru/system/Building.md) | [Bahasa Indonesia](../../id/system/Building.md) | [हिन्दी](../../hi/system/Building.md) | [العربية](../../ar/system/Building.md)

构建需要 Java 21、Android SDK API 37、NDK 27.3.13750724、CMake 3.22.1、stable Rust、aarch64-linux-android 与 x86_64-linux-android Android Rust targets、Cargo NDK 与 git submodules。需要运行 Kotlin/Android 检查、Rust fmt/clippy/tests 和模块 unit tests。

CI 同时验证 shell、SELinux、module template、Kotlin/Java/Rust、双架构、release/debug ZIP 与 Encryptor。First-party C 被禁止，`binder_interceptor.cpp` 是唯一允许的 first-party C++ Android ABI 边界。Release 使用 `./gradlew zipRelease`。
