# DRM Keystore Passthrough and Identifier Privacy

**语言:** [English](../../../system/DrmPassthrough.md) | [Türkçe](../../tr/system/DrmPassthrough.md) | **简体中文** | [Español](../../es/system/DrmPassthrough.md) | [Deutsch](../../de/system/DrmPassthrough.md) | [Русский](../../ru/system/DrmPassthrough.md) | [Bahasa Indonesia](../../id/system/DrmPassthrough.md) | [हिन्दी](../../hi/system/DrmPassthrough.md) | [العربية](../../ar/system/DrmPassthrough.md)

DRM Keystore Passthrough 让选定媒体应用保持在 Android 原生 Keystore certificate path。DRM Identifier Privacy 只针对支持的 stable AIDL DRM，在 `privacy=isolate` 应用读取 `deviceUniqueId` 时返回稳定的应用级假名，不用真实 DRM ID 作为派生输入。

`drm_packages.txt` 支持精确包名和有限 wildcard。在创建插件时记录发起应用的包名与运行时用户上下文（支持多用户与工作资料）。Privacy hook 只处理稳定 AIDL `IDrmFactory` / `IDrmPlugin.getPropertyByteArray("deviceUniqueId")`，不会修改 legacy HIDL、security level、license、provisioning、content key、session、HDCP 或 string properties。接口不符合预期时 fail open，保留原生响应。
