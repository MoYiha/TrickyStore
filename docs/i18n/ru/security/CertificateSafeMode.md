# Certificate Safe Mode

**Язык:** [English](../../../security/CertificateSafeMode.md) | [Türkçe](../../tr/security/CertificateSafeMode.md) | [简体中文](../../zh-CN/security/CertificateSafeMode.md) | [Español](../../es/security/CertificateSafeMode.md) | [Deutsch](../../de/security/CertificateSafeMode.md) | **Русский** | [Bahasa Indonesia](../../id/security/CertificateSafeMode.md) | [हिन्दी](../../hi/security/CertificateSafeMode.md) | [العربية](../../ar/security/CertificateSafeMode.md)

Legacy concept. Текущая WebUI не дает выключить core Keystore/TEE compatibility. Scope задают Global Mode/Application Rules, Spoof Engine управляет только identity.

`tee_broken_mode` может читаться для migration, но core targeting от него не зависит. Для диагностики следует уменьшать scope, использовать passthrough или контролируемо убирать key material.
