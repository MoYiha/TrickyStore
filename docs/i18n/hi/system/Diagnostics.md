# Diagnostics

**भाषा:** [English](../../../system/Diagnostics.md) | [Türkçe](../../tr/system/Diagnostics.md) | [简体中文](../../zh-CN/system/Diagnostics.md) | [Español](../../es/system/Diagnostics.md) | [Deutsch](../../de/system/Diagnostics.md) | [Русский](../../ru/system/Diagnostics.md) | [Bahasa Indonesia](../../id/system/Diagnostics.md) | **हिन्दी** | [العربية](../../ar/system/Diagnostics.md)

Dashboard में version, Engine, profile, keybox count, target size, RKP, DRM, native features देखें और Logs में पहला error खोजें। WebUI न खुले तो logcat, daemon, `webroot`, architecture-specific `webui_bridge` और manager state जांचें।

Info & Resources में Copy Diagnostics fixed allowlist और English keys वाला bounded support snapshot कॉपी करता है। इसमें version, root environment, native/interceptor state, aggregate keybox/rule count, process CPU/RSS और feature flags होते हैं; logs, package/keybox names, identity values, credentials, server configuration या key material नहीं। Feature flags module configuration बताते हैं, इसलिए साझा करने से पहले snapshot जांचें।

Isolation के लिए Minimal + reboot, genuine path verify, फिर features एक-एक करके enable करें। Effective State rule/profile, scope, template, keybox ref, privacy, features, patches, RKP/DRM, KeyMint/StrongBox, provider coexistence और reboot requirement दिखाता है, private keys नहीं।
