# Build Identity

**语言:** [English](../../../identity/BuildIdentity.md) | [Türkçe](../../tr/identity/BuildIdentity.md) | **简体中文** | [Español](../../es/identity/BuildIdentity.md) | [Deutsch](../../de/identity/BuildIdentity.md) | [Русский](../../ru/identity/BuildIdentity.md) | [Bahasa Indonesia](../../id/identity/BuildIdentity.md) | [हिन्दी](../../hi/identity/BuildIdentity.md) | [العربية](../../ar/identity/BuildIdentity.md)

Build Identity 将完整设备模板应用到 fingerprint 和支持的 app-visible Build 字段。它是可选功能，需要 Spoof Engine，并因 Android 在早期启动捕获这些值而需要重启。模板覆盖 manufacturer、model、brand、product、device、fingerprint、release、build ID、incremental、type、tags 和 security patch，任意 `ro.*` 输入会被拒绝。

Auto Identity 可从 Google 公共元数据解析 Pixel beta/canary Build Identity 并保存，但不会自动启用引擎。Build Identity、Security Patch、Region、Telephony、Attestation Identity 分别解析。
