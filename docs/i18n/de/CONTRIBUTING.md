# Contributing

**Sprache:** [English](../../../CONTRIBUTING.md) | [Türkçe](../tr/CONTRIBUTING.md) | [简体中文](../zh-CN/CONTRIBUTING.md) | [Español](../es/CONTRIBUTING.md) | **Deutsch** | [Русский](../ru/CONTRIBUTING.md) | [Bahasa Indonesia](../id/CONTRIBUTING.md) | [हिन्दी](../hi/CONTRIBUTING.md) | [العربية](../ar/CONTRIBUTING.md)

Änderungen müssen Fail-closed-Modell, Android 12-17 und KernelSU/APatch erhalten und keine nicht verifizierbare Hardware-Integrity behaupten. Kotlin/Android/Rust Checks sind erforderlich; portable native additions gehören in Rust, first-party C ist verboten und `binder_interceptor.cpp` die einzige C++-Ausnahme.

Binder/XML/ZIP/CBOX/HTTP/Paths/PIDs sind untrusted und brauchen Bounds und Failure Tests. Keine Private Keys, Keyboxes, Tokens, Secrets, generierten APKs/ZIPs committen. User-visible Änderungen benötigen Doku-Updates.
