# Contributing

**Dil:** [English](../../../CONTRIBUTING.md) | **Türkçe** | [简体中文](../zh-CN/CONTRIBUTING.md) | [Español](../es/CONTRIBUTING.md) | [Deutsch](../de/CONTRIBUTING.md) | [Русский](../ru/CONTRIBUTING.md) | [Bahasa Indonesia](../id/CONTRIBUTING.md) | [हिन्दी](../hi/CONTRIBUTING.md) | [العربية](../ar/CONTRIBUTING.md)

Değişiklikler fail-closed security model'i, Android 12-17 ve KernelSU/APatch kapsamını korumalıdır. Doğrulanamayan hardware-backed integrity iddiaları yapılmamalıdır. İlgili Kotlin/Android ve Rust checks çalıştırılmalı; portable native additions Rust olmalı, first-party C yasak, `binder_interceptor.cpp` tek first-party C++ istisnasıdır.

Binder parcels, XML, ZIP/CBOX, HTTP, paths ve process IDs untrusted kabul edilmelidir. Bounds explicit tutulmalı, malformed input ve failure paths için regression test eklenmeli, private key/keybox/token/device secret/generated APK/ZIP commit edilmemeli ve user-visible değişiklikte README/CHANGELOG güncellenmelidir.
