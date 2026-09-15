# Certificate Safe Mode

**Sprache:** [English](../../../security/CertificateSafeMode.md) | [Türkçe](../../tr/security/CertificateSafeMode.md) | [简体中文](../../zh-CN/security/CertificateSafeMode.md) | [Español](../../es/security/CertificateSafeMode.md) | **Deutsch** | [Русский](../../ru/security/CertificateSafeMode.md) | [Bahasa Indonesia](../../id/security/CertificateSafeMode.md) | [हिन्दी](../../hi/security/CertificateSafeMode.md) | [العربية](../../ar/security/CertificateSafeMode.md)

Legacy-Konzept. Die aktuelle WebUI bietet keinen Schalter zum Abschalten der Core-Keystore/TEE-Kompatibilität. Scope kommt aus Global Mode und Application Rules; Spoof Engine steuert nur Identitätswerte.

`tee_broken_mode` kann für Migration gelesen werden, ist aber nicht mehr Teil des Core-Targeting. Diagnose erfolgt durch engeren Scope, geeigneten Passthrough oder kontrolliertes Entfernen von Key Material.
