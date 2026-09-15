# Performance and Memory

**Dil:** [English](../../../system/Performance.md) | **Türkçe** | [简体中文](../../zh-CN/system/Performance.md) | [Español](../../es/system/Performance.md) | [Deutsch](../../de/system/Performance.md) | [Русский](../../ru/system/Performance.md) | [Bahasa Indonesia](../../id/system/Performance.md) | [हिन्दी](../../hi/system/Performance.md) | [العربية](../../ar/system/Performance.md)

Core Keystore interception servis sağlıklı olduğu sürece kayıtlı kalır. Spoof Engine kapalıyken optional identity, DRM privacy, build/region work ve gereksiz telephony path'leri park edilir; core certificate ve boot protection aktif kalır. Automatic Keybox Check kendi kontrolüne sahiptir.

Rust Binder parser fixed caller-owned array kullanır; descriptor cache 64 fixed slot'tur ve heap büyümez. DRM controller tracked factory/service sayısını sınırlar ve busy poll yapmaz. Package, rule, DRM, RKP, certificate, patch, template ve keybox cache'leri entry/byte limitlidir. Release Rust LTO, size optimization ve hardened native linking seçenekleri kullanır.
