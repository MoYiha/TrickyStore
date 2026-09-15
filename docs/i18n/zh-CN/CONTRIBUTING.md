# Contributing

**语言:** [English](../../../CONTRIBUTING.md) | [Türkçe](../tr/CONTRIBUTING.md) | **简体中文** | [Español](../es/CONTRIBUTING.md) | [Deutsch](../de/CONTRIBUTING.md) | [Русский](../ru/CONTRIBUTING.md) | [Bahasa Indonesia](../id/CONTRIBUTING.md) | [हिन्दी](../hi/CONTRIBUTING.md) | [العربية](../ar/CONTRIBUTING.md)

贡献必须保持 fail-closed security model、Android 12-17 与 KernelSU/APatch 范围，并避免无法验证的 hardware-backed integrity 声明。运行相关 Kotlin/Android 与 Rust checks；可移植 native 新增必须用 Rust，first-party C 禁止，`binder_interceptor.cpp` 是唯一 first-party C++ 例外。

Binder/XML/ZIP/CBOX/HTTP/path/PID 都视为不可信，必须显式限制，变化行为需要失败路径 regression tests。不要提交 private keys、keyboxes、tokens、device secrets、generated APK/ZIP，并同步用户文档。
