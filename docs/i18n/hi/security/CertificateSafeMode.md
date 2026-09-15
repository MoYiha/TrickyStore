# Certificate Safe Mode

**भाषा:** [English](../../../security/CertificateSafeMode.md) | [Türkçe](../../tr/security/CertificateSafeMode.md) | [简体中文](../../zh-CN/security/CertificateSafeMode.md) | [Español](../../es/security/CertificateSafeMode.md) | [Deutsch](../../de/security/CertificateSafeMode.md) | [Русский](../../ru/security/CertificateSafeMode.md) | [Bahasa Indonesia](../../id/security/CertificateSafeMode.md) | **हिन्दी** | [العربية](../../ar/security/CertificateSafeMode.md)

Legacy concept है। Current WebUI core Keystore/TEE compatibility off करने का switch नहीं देता। Scope Global Mode/Application Rules तय करते हैं, Spoof Engine केवल identity।

`tee_broken_mode` migration के लिए पढ़ा जा सकता है पर core targeting पर निर्भर नहीं। Diagnosis में scope कम करें, passthrough उपयोग करें या controlled key material हटाएं।
