# Identity Refresh

**语言:** [English](../../../identity/IdentityRefresh.md) | [Türkçe](../../tr/identity/IdentityRefresh.md) | **简体中文** | [Español](../../es/identity/IdentityRefresh.md) | [Deutsch](../../de/identity/IdentityRefresh.md) | [Русский](../../ru/identity/IdentityRefresh.md) | [Bahasa Indonesia](../../id/identity/IdentityRefresh.md) | [हिन्दी](../../hi/identity/IdentityRefresh.md) | [العربية](../../ar/identity/IdentityRefresh.md)

Identity Refresh 为下一次启动准备新的 validated app-facing identity，而不改变当前 boot 的 active snapshot。Early boot 会验证 staged file 的 path/type/size/permission/control，再原子 promote，随后 Build properties 与 service 使用同一 snapshot。

IMEI/ICCID checksum、数字长度和 serial charset 都有界。手动编辑会丢弃旧 staged snapshot；在启动前关闭 Spoof Engine 或 Identity Refresh 会阻止不希望的 promotion。
