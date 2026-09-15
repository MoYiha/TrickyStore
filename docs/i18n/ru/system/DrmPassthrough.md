# DRM Keystore Passthrough and Identifier Privacy

**Язык:** [English](../../../system/DrmPassthrough.md) | [Türkçe](../../tr/system/DrmPassthrough.md) | [简体中文](../../zh-CN/system/DrmPassthrough.md) | [Español](../../es/system/DrmPassthrough.md) | [Deutsch](../../de/system/DrmPassthrough.md) | **Русский** | [Bahasa Indonesia](../../id/system/DrmPassthrough.md) | [हिन्दी](../../hi/system/DrmPassthrough.md) | [العربية](../../ar/system/DrmPassthrough.md)

Passthrough держит выбранные media apps на genuine Android Keystore certificate path. Identifier Privacy заменяет только supported stable-AIDL `deviceUniqueId` для `privacy=isolate` стабильным app-scoped pseudonym без использования genuine DRM ID в derivation.

`drm_packages.txt` поддерживает exact packages и bounded wildcard. При создании плагина фиксируются имя пакета и контекст пользователя (multi-user / work-profile). Privacy hook ограничен `IDrmFactory` / `IDrmPlugin.getPropertyByteArray("deviceUniqueId")`; HIDL, security level, licenses, provisioning, keys, sessions, HDCP и string properties не меняются. Неожиданный ABI сохраняет original response fail open.
