# Attestation

**Bahasa:** [English](../../../security/Attestation.md) | [Türkçe](../../tr/security/Attestation.md) | [简体中文](../../zh-CN/security/Attestation.md) | [Español](../../es/security/Attestation.md) | [Deutsch](../../de/security/Attestation.md) | [Русский](../../ru/security/Attestation.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/Attestation.md) | [العربية](../../ar/security/Attestation.md)

Lapisan attestation memberikan kompatibilitas rantai sertifikat terkontrol untuk aplikasi terpilih sambil mempertahankan pembuatan kunci Android asli dan operasi kriptografi berikutnya.

Caller infrastruktur RKP selalu tetap di jalur provisioning Android asli. Untuk UID aplikasi target, respons `generateKey` yang berhasil dan pembacaan sertifikat `getKeyEntry` berikutnya memakai satu jalur kompatibilitas agar satu alias tidak menampilkan attestation leaf yang berbeda.

Operasi private key tetap dilakukan Android KeyMint atau StrongBox. Sebelum material aktif, kecocokan key/certificate, algoritma, chain, masa berlaku, ambiguity, dan revocation diperiksa. Substitusi sertifikat tidak menciptakan hardware root of trust, mengunci bootloader secara fisik, atau menjamin remote verdict.
