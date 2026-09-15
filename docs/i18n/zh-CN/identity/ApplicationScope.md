# Application Scope

**语言:** [English](../../../identity/ApplicationScope.md) | [Türkçe](../../tr/identity/ApplicationScope.md) | **简体中文** | [Español](../../es/identity/ApplicationScope.md) | [Deutsch](../../de/identity/ApplicationScope.md) | [Русский](../../ru/identity/ApplicationScope.md) | [Bahasa Indonesia](../../id/identity/ApplicationScope.md) | [हिन्दी](../../hi/identity/ApplicationScope.md) | [العربية](../../ar/identity/ApplicationScope.md)

Application Scope 决定哪些 Android 应用 UID 可以接收证书/密钥盒或身份兼容处理。模块包含两组独立的目标文件与全局模式：

- **Keybox 目标 (`target.txt`)**：在全局 Keybox 模式关闭时，指定接收自定义 Keybox 和 TEE 认证证书重写的应用。
- **Identity 目标 (`identity_target.txt`)**：在全局身份模式关闭时，指定接收单应用身份覆盖（Build、Telephony、Region）的应用。
- **全局 Keybox 模式**：对所有用户应用启用 Keybox/认证伪装，无需在 `target.txt` 中单独配置。系统与基础设施 UID 保持原生保护。
- **全局 Identity 模式**：在系统级向所有应用应用 Build 属性。关闭时仅作用于 `identity_target.txt` 及已分配档案的应用。
- **独立安全补丁模块**：安全补丁可脱离身份引擎在仪表盘中独立开关与配置。

共享 UID 的应用在 Binder 层属于同一调用身份。非法更新会被 fail-closed 拦截，保留最后一个有效状态。
