# Contributing

**भाषा:** [English](../../../CONTRIBUTING.md) | [Türkçe](../tr/CONTRIBUTING.md) | [简体中文](../zh-CN/CONTRIBUTING.md) | [Español](../es/CONTRIBUTING.md) | [Deutsch](../de/CONTRIBUTING.md) | [Русский](../ru/CONTRIBUTING.md) | [Bahasa Indonesia](../id/CONTRIBUTING.md) | **हिन्दी** | [العربية](../ar/CONTRIBUTING.md)

Fail-closed model, Android 12-17, KernelSU/APatch scope बनाए रखें और unverifiable hardware claims न करें। Kotlin/Android/Rust checks चलाएँ; portable native additions Rust में, first-party C prohibited, `binder_interceptor.cpp` एकमात्र C++ exception।

Binder/XML/ZIP/CBOX/HTTP/path/PID untrusted हैं और bounds/failure tests चाहिए। Private keys/keyboxes/tokens/secrets/generated APK/ZIP commit न करें। User-visible changes पर docs update करें।
