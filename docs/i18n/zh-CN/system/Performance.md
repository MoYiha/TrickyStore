# Performance and Memory

**语言:** [English](../../../system/Performance.md) | [Türkçe](../../tr/system/Performance.md) | **简体中文** | [Español](../../es/system/Performance.md) | [Deutsch](../../de/system/Performance.md) | [Русский](../../ru/system/Performance.md) | [Bahasa Indonesia](../../id/system/Performance.md) | [हिन्दी](../../hi/system/Performance.md) | [العربية](../../ar/system/Performance.md)

核心 Keystore interception 在服务健康时持续注册。Spoof Engine 关闭会停用不必要的 optional identity、DRM privacy、build/region 和 telephony 工作，而 core certificate 与 boot protection 继续运行。Automatic Keybox Check 有独立开关。

Rust Binder parser 使用固定数组，descriptor cache 为 64 个固定槽。DRM controller、package/rule/certificate/keybox 等缓存都有严格 entry/byte 上限，并避免 busy polling。Release Rust 使用 LTO、size optimization 与 hardened linker 设置。
