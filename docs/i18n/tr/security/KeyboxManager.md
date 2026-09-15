# Keybox Manager

**Dil:** [English](../../../security/KeyboxManager.md) | **Türkçe** | [简体中文](../../zh-CN/security/KeyboxManager.md) | [Español](../../es/security/KeyboxManager.md) | [Deutsch](../../de/security/KeyboxManager.md) | [Русский](../../ru/security/KeyboxManager.md) | [Bahasa Indonesia](../../id/security/KeyboxManager.md) | [हिन्दी](../../hi/security/KeyboxManager.md) | [العربية](../../ar/security/KeyboxManager.md)

Keybox Manager authorized attestation key material'i yükler, doğrular, seçer ve izler. Legacy tek file, çoklu XML ve encrypted CBOX desteklenir. Application Rule belirli doğrulanmış file seçebilir; remote source verisi de aynı local validation tamamlanana kadar untrusted kabul edilir.

Her private key leaf certificate ile eşleşmeli; algorithm, chain, dates, duplicate/ambiguity ve revocation kontrol edilir. Geçerli anahtarlar açılış anında ağ bağlantısı beklenmeden anında devreye girer. Automatic Keybox Check açıkken iptal kontrolleri arka planda yapılır; kapalıyken kullanıcı tanımlı veya iptal edilmiş anahtarlar reddedilmez. Bozuk girdi içeren havuz komple reddedilir. Gerçek keybox source control'e commit edilmemelidir.
