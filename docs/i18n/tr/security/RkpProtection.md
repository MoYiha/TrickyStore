# RKP Protection

**Dil:** [English](../../../security/RkpProtection.md) | **Türkçe** | [简体中文](../../zh-CN/security/RkpProtection.md) | [Español](../../es/security/RkpProtection.md) | [Deutsch](../../de/security/RkpProtection.md) | [Русский](../../ru/security/RkpProtection.md) | [Bahasa Indonesia](../../id/security/RkpProtection.md) | [हिन्दी](../../hi/security/RkpProtection.md) | [العربية](../../ar/security/RkpProtection.md)

Remote Key Provisioning koruması Android provisioning altyapısını gerçek platform yolunda tutar. Android/Google RKP ve eski Remote Provisioner paketleri substitution scope dışında kalır; sistem UID'leri ve çözülemeyen package durumları da fail closed davranır.

RKP altyapı caller'ları hiçbir zaman değiştirilmez. Hedeflenen uygulama UID'lerinde `generateKey` ve sonraki `getKeyEntry` sertifika yanıtları aynı compatibility yolunu kullanır; bu, tek alias'ın iki farklı attestation leaf göstermesini önler.

Eski `rkp_passthrough` switch'i retired durumdadır. Eski config/backup içinde işaret bulunabilir ancak generated-key davranışını artık yönetmez ve WebUI runtime toggle olarak sunmaz. Built-in profiller RKP davranışını değiştirmez; RKP altyapı koruması her zaman aktiftir.

CleveresTricky bir RKP sunucusu simüle etmez, provisioning credential üretmez veya hardware provisioning root'u değiştirmez.
