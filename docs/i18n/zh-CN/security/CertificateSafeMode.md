# Certificate Safe Mode

**语言:** [English](../../../security/CertificateSafeMode.md) | [Türkçe](../../tr/security/CertificateSafeMode.md) | **简体中文** | [Español](../../es/security/CertificateSafeMode.md) | [Deutsch](../../de/security/CertificateSafeMode.md) | [Русский](../../ru/security/CertificateSafeMode.md) | [Bahasa Indonesia](../../id/security/CertificateSafeMode.md) | [हिन्दी](../../hi/security/CertificateSafeMode.md) | [العربية](../../ar/security/CertificateSafeMode.md)

Certificate Safe Mode 是旧版配置概念。当前 WebUI 不提供关闭核心 Keystore/TEE compatibility 的开关。Global Mode 与 Application Rules 决定 scope，而 Spoof Engine 只控制身份值。

旧安装中的 `tee_broken_mode` 仅用于迁移/兼容读取，核心 targeting 不再依赖它。排查时应缩小 scope、使用合适 passthrough，或在受控环境移除相关 key material。
