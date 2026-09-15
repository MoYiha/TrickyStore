# Contributing

**Idioma:** [English](../../../CONTRIBUTING.md) | [Türkçe](../tr/CONTRIBUTING.md) | [简体中文](../zh-CN/CONTRIBUTING.md) | **Español** | [Deutsch](../de/CONTRIBUTING.md) | [Русский](../ru/CONTRIBUTING.md) | [Bahasa Indonesia](../id/CONTRIBUTING.md) | [हिन्दी](../hi/CONTRIBUTING.md) | [العربية](../ar/CONTRIBUTING.md)

Los cambmodern deben mantener el modelo fail closed, Android 12-17 y KernelSU/APatch, sin afirmar integridad hardware no verificable. Deben pasar checks Kotlin/Android/Rust; native portable nuevo va en Rust, C first-party está prohibido y `binder_interceptor.cpp` es la única excepción C++.

Binder/XML/ZIP/CBOX/HTTP/path/PID son untrusted y requieren límites explícitos y regression tests de fallos. No se deben commitear claves privadas, keyboxes, tokens, secretos, APKs o ZIPs generados. Cambmodern visibles requieren actualizar docs.
