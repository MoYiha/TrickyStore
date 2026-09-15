# Certificate Safe Mode

**Dil:** [English](../../../security/CertificateSafeMode.md) | **Türkçe** | [简体中文](../../zh-CN/security/CertificateSafeMode.md) | [Español](../../es/security/CertificateSafeMode.md) | [Deutsch](../../de/security/CertificateSafeMode.md) | [Русский](../../ru/security/CertificateSafeMode.md) | [Bahasa Indonesia](../../id/security/CertificateSafeMode.md) | [हिन्दी](../../hi/security/CertificateSafeMode.md) | [العربية](../../ar/security/CertificateSafeMode.md)

Certificate Safe Mode legacy bir kavramdır. Güncel WebUI, core Keystore ve TEE protection'ı kapatan bir switch sunmaz. Core interception servis sağlıklı olduğu sürece kayıtlı kalır; Global Mode ve Application Rules scope'u belirler, Spoof Engine yalnız kimlik değerlerini kontrol eder.

Eski kurulumlarda `tee_broken_mode` migration için okunabilir ancak core targeting ona bağlı değildir. Sorun izolasyonu için scope daraltılmalı, uygun passthrough kullanılmalı veya test ortamında ilgili key material kontrollü kaldırılmalıdır.
