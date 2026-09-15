# Contributing

**Bahasa:** [English](../../../CONTRIBUTING.md) | [Türkçe](../tr/CONTRIBUTING.md) | [简体中文](../zh-CN/CONTRIBUTING.md) | [Español](../es/CONTRIBUTING.md) | [Deutsch](../de/CONTRIBUTING.md) | [Русский](../ru/CONTRIBUTING.md) | **Bahasa Indonesia** | [हिन्दी](../hi/CONTRIBUTING.md) | [العربية](../ar/CONTRIBUTING.md)

Pertahankan fail-closed model, Android 12-17, KernelSU/APatch dan jangan membuat klaim hardware integrity yang tidak dapat diverifikasi. Jalankan Kotlin/Android/Rust checks; portable native additions di Rust, first-party C dilarang, `binder_interceptor.cpp` satu-satunya C++ exception.

Binder/XML/ZIP/CBOX/HTTP/path/PID untrusted dan membutuhkan bounds/failure tests. Jangan commit private key, keybox, token, secret, generated APK/ZIP. Update docs untuk perubahan user-visible.
