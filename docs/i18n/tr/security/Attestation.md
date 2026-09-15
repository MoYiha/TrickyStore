# Attestation

**Dil:** [English](../../../security/Attestation.md) | **Türkçe** | [简体中文](../../zh-CN/security/Attestation.md) | [Español](../../es/security/Attestation.md) | [Deutsch](../../de/security/Attestation.md) | [Русский](../../ru/security/Attestation.md) | [Bahasa Indonesia](../../id/security/Attestation.md) | [हिन्दी](../../hi/security/Attestation.md) | [العربية](../../ar/security/Attestation.md)

Attestation katmanı, seçili uygulamalara kontrollü sertifika zinciri uyumluluğu sağlarken gerçek Android anahtar oluşturma ve sonraki kriptografik işlemleri korur.

RKP altyapı çağıranları her zaman Android'in gerçek provisioning yolunda kalır. Hedeflenen uygulama UID'lerinde başarılı `generateKey` yanıtları ile sonraki `getKeyEntry` sertifika okumaları aynı sertifika uyumluluk yolunu kullanır; böylece aynı alias iki farklı attestation leaf göstermez.

Private key işlemi istenen güvenlik seviyesinde Android KeyMint veya StrongBox tarafından yapılmaya devam eder. Etkinleştirmeden önce key/certificate eşleşmesi, algoritma, chain yapısı, geçerlilik, ambiguity ve revocation doğrulanır. Sertifika değiştirme fiziksel hardware root of trust oluşturmaz, bootloader'ı kilitlemez veya remote verdict garanti etmez.
