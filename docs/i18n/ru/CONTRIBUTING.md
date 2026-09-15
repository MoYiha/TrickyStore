# Contributing

**Язык:** [English](../../../CONTRIBUTING.md) | [Türkçe](../tr/CONTRIBUTING.md) | [简体中文](../zh-CN/CONTRIBUTING.md) | [Español](../es/CONTRIBUTING.md) | [Deutsch](../de/CONTRIBUTING.md) | **Русский** | [Bahasa Indonesia](../id/CONTRIBUTING.md) | [हिन्दी](../hi/CONTRIBUTING.md) | [العربية](../ar/CONTRIBUTING.md)

Сохранять fail-closed model, Android 12-17, KernelSU/APatch и не делать unverifiable hardware claims. Нужны Kotlin/Android/Rust checks; portable native на Rust, first-party C запрещен, `binder_interceptor.cpp` единственная C++ exception.

Binder/XML/ZIP/CBOX/HTTP/path/PID untrusted и требуют bounds/failure tests. Не коммитить private keys/keyboxes/tokens/secrets/generated APK/ZIP. User-visible changes требуют docs update.
