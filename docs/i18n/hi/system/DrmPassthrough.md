# DRM Keystore Passthrough and Identifier Privacy

**भाषा:** [English](../../../system/DrmPassthrough.md) | [Türkçe](../../tr/system/DrmPassthrough.md) | [简体中文](../../zh-CN/system/DrmPassthrough.md) | [Español](../../es/system/DrmPassthrough.md) | [Deutsch](../../de/system/DrmPassthrough.md) | [Русский](../../ru/system/DrmPassthrough.md) | [Bahasa Indonesia](../../id/system/DrmPassthrough.md) | **हिन्दी** | [العربية](../../ar/system/DrmPassthrough.md)

Passthrough selected media apps को genuine Android Keystore certificate path पर रखता है। Identifier Privacy केवल supported stable-AIDL `deviceUniqueId` को `privacy=isolate` apps के लिए stable app-scoped pseudonym से बदलता है, genuine DRM ID derivation input नहीं है।

`drm_packages.txt` exact package/bounded wildcard support करता है। Plugin निर्माण के समय पैकेज नाम और रनटाइम यूज़र कॉन्टेक्स्ट (multi-user / work-profile) कैप्चर किया जाता है। Hook `IDrmFactory` / `IDrmPlugin.getPropertyByteArray("deviceUniqueId")` तक सीमित है; HIDL, security level, license, provisioning, keys, sessions, HDCP, string property नहीं बदलते। Unexpected ABI fail open रहता है।
