# Building

**اللغة:** [English](../../../system/Building.md) | [Türkçe](../../tr/system/Building.md) | [简体中文](../../zh-CN/system/Building.md) | [Español](../../es/system/Building.md) | [Deutsch](../../de/system/Building.md) | [Русский](../../ru/system/Building.md) | [Bahasa Indonesia](../../id/system/Building.md) | [हिन्दी](../../hi/system/Building.md) | **العربية**

يتطلب Java 21 وSDK API 37 وNDK 27.3.13750724 وCMake 3.22.1 وstable Rust وaarch64-linux-android وx86_64-linux-android Android targets وCargo NDK وsubmodules. يجب نجاح Kotlin/Android checks وRust fmt/clippy/tests وunit tests.

CI يتحقق من shell وSELinux وtemplate وKotlin/Java/Rust والمعماريتين وrelease/debug ZIP وEncryptor. First-party C ممنوع و`binder_interceptor.cpp` هو استثناء C++ الوحيد. Release عبر `./gradlew zipRelease`.
