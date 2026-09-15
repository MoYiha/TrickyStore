# Performance and Memory

**اللغة:** [English](../../../system/Performance.md) | [Türkçe](../../tr/system/Performance.md) | [简体中文](../../zh-CN/system/Performance.md) | [Español](../../es/system/Performance.md) | [Deutsch](../../de/system/Performance.md) | [Русский](../../ru/system/Performance.md) | [Bahasa Indonesia](../../id/system/Performance.md) | [हिन्दी](../../hi/system/Performance.md) | **العربية**

Core Keystore interception يبقى فعالا؛ إيقاف Spoof Engine يوقف optional identity/DRM/build/region/telephony work. Automatic Keybox Check له control مستقل.

Binder parser يستخدم fixed arrays وdescriptor cache من 64 slot. Controller/cache محدودة وتتجنب busy poll. Rust release يستخدم LTO وsize optimization وhardened linking.
