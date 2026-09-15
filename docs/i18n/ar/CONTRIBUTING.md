# Contributing

**اللغة:** [English](../../../CONTRIBUTING.md) | [Türkçe](../tr/CONTRIBUTING.md) | [简体中文](../zh-CN/CONTRIBUTING.md) | [Español](../es/CONTRIBUTING.md) | [Deutsch](../de/CONTRIBUTING.md) | [Русский](../ru/CONTRIBUTING.md) | [Bahasa Indonesia](../id/CONTRIBUTING.md) | [हिन्दी](../hi/CONTRIBUTING.md) | **العربية**

يجب الحفاظ على fail-closed model وAndroid 12-17 وKernelSU/APatch وعدم تقديم claims غير قابلة للتحقق عن hardware integrity. شغل Kotlin/Android/Rust checks؛ portable native additions في Rust، first-party C ممنوع و`binder_interceptor.cpp` استثناء C++ الوحيد.

Binder/XML/ZIP/CBOX/HTTP/path/PID untrusted وتحتاج bounds/failure tests. لا ترفع private keys/keyboxes/tokens/secrets/generated APK/ZIP. حدث docs عند تغيير user-visible behavior.
