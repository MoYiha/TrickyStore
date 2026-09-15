# DRM Keystore Passthrough and Identifier Privacy

**Dil:** [English](../../../system/DrmPassthrough.md) | **Türkçe** | [简体中文](../../zh-CN/system/DrmPassthrough.md) | [Español](../../es/system/DrmPassthrough.md) | [Deutsch](../../de/system/DrmPassthrough.md) | [Русский](../../ru/system/DrmPassthrough.md) | [Bahasa Indonesia](../../id/system/DrmPassthrough.md) | [हिन्दी](../../hi/system/DrmPassthrough.md) | [العربية](../../ar/system/DrmPassthrough.md)

DRM Keystore Passthrough seçili medya uygulamalarını Android'in gerçek Keystore certificate path'inde tutar. DRM Identifier Privacy ise desteklenen stable AIDL DRM yolunda `privacy=isolate` kullanılan uygulamanın `deviceUniqueId` byte array okumasını, gerçek değeri input olarak kullanmadan, stable app-scoped pseudonym ile değiştirir.

`drm_packages.txt` exact paket ve bounded wildcard kabul eder. Plugin oluşturma sırasında çağrıyı başlatan uygulamanın paket adı ve çalışma zamanı kullanıcı bağlamı (multi-user / work-profile) kaydedilir. Privacy hook yalnız `IDrmFactory`/`IDrmPlugin.getPropertyByteArray("deviceUniqueId")` yolunu hedefler; legacy HIDL veya vendor-specific yolları değiştirmez. Security level, licenses, provisioning, content keys, sessions, HDCP, string properties ve DRM policy değiştirilmez. Beklenen AIDL servis veya transaction yapısı yoksa fail open ile gerçek yanıt korunur.
