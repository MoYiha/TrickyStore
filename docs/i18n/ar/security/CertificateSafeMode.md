# Certificate Safe Mode

**اللغة:** [English](../../../security/CertificateSafeMode.md) | [Türkçe](../../tr/security/CertificateSafeMode.md) | [简体中文](../../zh-CN/security/CertificateSafeMode.md) | [Español](../../es/security/CertificateSafeMode.md) | [Deutsch](../../de/security/CertificateSafeMode.md) | [Русский](../../ru/security/CertificateSafeMode.md) | [Bahasa Indonesia](../../id/security/CertificateSafeMode.md) | [हिन्दी](../../hi/security/CertificateSafeMode.md) | **العربية**

مفهوم legacy. WebUI الحالية لا تقدم مفتاحا لإيقاف core Keystore/TEE compatibility. Scope يحدده Global Mode/Application Rules وSpoof Engine يخص الهوية فقط.

يمكن قراءة `tee_broken_mode` للمهاجرة لكنه لا يحدد core targeting. للتشخيص قلل scope أو استخدم passthrough أو أزل key material بشكل مضبوط.
