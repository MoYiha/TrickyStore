# Remote Sources

**语言:** [English](../../../system/RemoteSources.md) | [Türkçe](../../tr/system/RemoteSources.md) | **简体中文** | [Español](../../es/system/RemoteSources.md) | [Deutsch](../../de/system/RemoteSources.md) | [Русский](../../ru/system/RemoteSources.md) | [Bahasa Indonesia](../../id/system/RemoteSources.md) | [हिन्दी](../../hi/system/RemoteSources.md) | [العربية](../../ar/system/RemoteSources.md)

Remote Sources 只从明确配置的 HTTPS endpoint 获取授权 keybox material。Host/port/path/timeout/refresh/auth/header/response size 均有界，secret 不出现在 status response。

可要求签名内容。Signature、XML/CBOX、size、keybox、certificate 与 revocation 检查完成前数据不会激活。失败刷新不会用坏下载覆盖已有 verified material。
