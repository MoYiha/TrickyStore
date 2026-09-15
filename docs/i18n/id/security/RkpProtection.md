# RKP Protection

**Bahasa:** [English](../../../security/RkpProtection.md) | [Türkçe](../../tr/security/RkpProtection.md) | [简体中文](../../zh-CN/security/RkpProtection.md) | [Español](../../es/security/RkpProtection.md) | [Deutsch](../../de/security/RkpProtection.md) | [Русский](../../ru/security/RkpProtection.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/RkpProtection.md) | [العربية](../../ar/security/RkpProtection.md)

Perlindungan Remote Key Provisioning menjaga infrastruktur provisioning Android pada jalur platform asli. Paket RKP Android/Google dan Remote Provisioner legacy selalu di luar scope substitusi; UID sistem dan resolusi package yang tidak diketahui juga fail closed.

Caller infrastruktur RKP tidak pernah dimodifikasi. Untuk UID aplikasi target, `generateKey` dan respons sertifikat `getKeyEntry` berikutnya memakai jalur kompatibilitas terpadu sehingga satu alias tidak menghasilkan dua attestation leaf berbeda.

Switch lama `rkp_passthrough` sudah retired. Marker dapat tetap ada di config atau backup lama, tetapi tidak lagi mengontrol generated-key dan tidak diekspos sebagai runtime toggle WebUI. Built-in Profiles tidak mengubah perilaku RKP; perlindungan infrastrukturnya selalu aktif.

CleveresTricky tidak mensimulasikan server RKP, membuat provisioning credential, atau mengubah hardware provisioning root.
