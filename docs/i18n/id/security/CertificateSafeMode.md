# Certificate Safe Mode

**Bahasa:** [English](../../../security/CertificateSafeMode.md) | [Türkçe](../../tr/security/CertificateSafeMode.md) | [简体中文](../../zh-CN/security/CertificateSafeMode.md) | [Español](../../es/security/CertificateSafeMode.md) | [Deutsch](../../de/security/CertificateSafeMode.md) | [Русский](../../ru/security/CertificateSafeMode.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/CertificateSafeMode.md) | [العربية](../../ar/security/CertificateSafeMode.md)

Konsep legacy. WebUI saat ini tidak memiliki switch untuk mematikan core Keystore/TEE compatibility. Scope diatur Global Mode/Application Rules, Spoof Engine hanya identity.

`tee_broken_mode` dapat dibaca untuk migration tetapi tidak menentukan core targeting. Diagnosis dilakukan dengan mempersempit scope, memakai passthrough, atau menghapus key material secara terkontrol.
