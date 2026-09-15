# Application Rules

**语言:** [English](../../../identity/ApplicationRules.md) | [Türkçe](../../tr/identity/ApplicationRules.md) | **简体中文** | [Español](../../es/identity/ApplicationRules.md) | [Deutsch](../../de/identity/ApplicationRules.md) | [Русский](../../ru/identity/ApplicationRules.md) | [Bahasa Indonesia](../../id/identity/ApplicationRules.md) | [हिन्दी](../../hi/identity/ApplicationRules.md) | [العربية](../../ar/identity/ApplicationRules.md)

Application Rules 可以为符合条件的应用分配设备模板、经过验证的本地 keybox 或隐私策略。有效规则本身就是明确目标，因此不需要额外 scope 条目。`inherit` 保持全局策略；`isolate` 从受保护随机种子派生稳定的应用级 IMEI、IMSI、ICCID、MEID、电话号码、序列号、支持的 attestation 标识以及现代 DRM `deviceUniqueId` 假名；`redact` 对支持的 telephony 与 attestation 标识返回空值，同时保留 Android 权限失败。

Attestation 身份替换需要有效且已验证的 keybox。DRM identifier isolation 与 DRM Keystore Passthrough 相互独立。Shared UID 的包会被作为同一确定性上下文处理，真实包名来自 Package Manager，而不是请求内容。规则以有界 trie 和不可变 snapshot 管理，成功 reload 时相关缓存同步清理。
