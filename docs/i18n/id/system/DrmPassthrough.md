# DRM Keystore Passthrough and Identifier Privacy

**Bahasa:** [English](../../../system/DrmPassthrough.md) | [Türkçe](../../tr/system/DrmPassthrough.md) | [简体中文](../../zh-CN/system/DrmPassthrough.md) | [Español](../../es/system/DrmPassthrough.md) | [Deutsch](../../de/system/DrmPassthrough.md) | [Русский](../../ru/system/DrmPassthrough.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/system/DrmPassthrough.md) | [العربية](../../ar/system/DrmPassthrough.md)

Passthrough menjaga media apps tertentu pada genuine Android Keystore certificate path. Identifier Privacy hanya mengganti supported stable-AIDL `deviceUniqueId` untuk `privacy=isolate` dengan pseudonim stabil per aplikasi, tanpa genuine DRM ID sebagai input derivation.

`drm_packages.txt` mendukung exact package/wildcard terbatas. Saat plugin dibuat, nama paket dan konteks pengguna (multi-user / work-profile) dicatat. Hook hanya `IDrmFactory` / `IDrmPlugin.getPropertyByteArray("deviceUniqueId")`; HIDL, security level, license, provisioning, content key, session, HDCP dan string property tidak berubah. Unexpected ABI fail open.
