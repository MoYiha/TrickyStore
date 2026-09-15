# Application Scope

**Dil:** [English](../../../identity/ApplicationScope.md) | **Türkçe** | [简体中文](../../zh-CN/identity/ApplicationScope.md) | [Español](../../es/identity/ApplicationScope.md) | [Deutsch](../../de/identity/ApplicationScope.md) | [Русский](../../ru/identity/ApplicationScope.md) | [Bahasa Indonesia](../../id/identity/ApplicationScope.md) | [हिन्दी](../../hi/identity/ApplicationScope.md) | [العربية](../../ar/identity/ApplicationScope.md)

Application Scope, hangi Android uygulamalarının sertifika/keybox veya kimlik (Identity) uyumluluğu alacağını belirler. Modül iki ayrı hedef dosyası ve iki ayrı global mod içerir:

- **Keybox Hedefleri (`target.txt`)**: Global Keybox kapalıyken özel Keybox ve TEE attestation sertifika değişikliğinin uygulanacağı paketleri tanımlar.
- **Identity Hedefleri (`identity_target.txt`)**: Global Identity kapalıyken uygulama bazlı kimlik (Build, Telephony, Region) özelliklerinin uygulanacağı paketleri tanımlar.
- **Global Keybox Modu**: Tüm kullanıcı uygulamalarına `target.txt` gerekmeksizin özel Keybox uygular. Sistem ve altyapı UID'leri korunur.
- **Global Identity Modu**: Sistem seviyesinde `ro.*` build özelliklerini tüm cihaz için uygular. Kapalıyken kimlik yalnızca `identity_target.txt` ve atanmış profillere etki eder.
- **Bağımsız Security Patch**: Güvenlik yaması modülü Identity motorundan bağımsız olarak Dashboard üzerinden yönetilebilir.

Shared UID kullanan paketler Binder açısından aynı kimliği paylaşır. Geçersiz güncellemeler fail-closed olarak reddedilir ve son geçerli durum korunur.
