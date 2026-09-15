# Security Model

**语言:** [English](../../../security/SecurityModel.md) | [Türkçe](../../tr/security/SecurityModel.md) | **简体中文** | [Español](../../es/security/SecurityModel.md) | [Deutsch](../../de/security/SecurityModel.md) | [Русский](../../ru/security/SecurityModel.md) | [Bahasa Indonesia](../../id/security/SecurityModel.md) | [हिन्दी](../../hi/security/SecurityModel.md) | [العربية](../../ar/security/SecurityModel.md)

Root service、OS、KernelSU/APatch、installed module files 和明确授权 key material 属于本地 trusted boundary。App、Binder content、upload、remote response、config edit、archive、rules、templates、paths 和 network metadata 都是不可信输入。

配置目录必须真实、root-owned，敏感文件 root-only，symlink 被拒绝，写入原子化。Native parser 先验证 live Binder ABI，再通过 kernel-validated bounded copy 解析。Injector 限制 symbol/process/library，WebUI 不开放 TCP port，只通过 strict native bridge/queue。恶意 root process 仍超出可完全防御范围。
