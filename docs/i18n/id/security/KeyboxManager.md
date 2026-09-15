# Keybox Manager

**Bahasa:** [English](../../../security/KeyboxManager.md) | [Türkçe](../../tr/security/KeyboxManager.md) | [简体中文](../../zh-CN/security/KeyboxManager.md) | [Español](../../es/security/KeyboxManager.md) | [Deutsch](../../de/security/KeyboxManager.md) | [Русский](../../ru/security/KeyboxManager.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/KeyboxManager.md) | [العربية](../../ar/security/KeyboxManager.md)

Memuat, memverifikasi, memilih dan memonitor authorized attestation material dalam legacy/XML/CBOX. Application Rule dapat memilih file; remote material untrusted sampai lulus verifikasi lokal.

Private key harus cocok dengan leaf certificate; algorithm, chain, date, duplicate/ambiguity, revocation diperiksa. Key material yang valid diaktifkan langsung saat boot tanpa menunggu jaringan. Saat Automatic Keybox Check aktif, verifikasi pencabutan dilakukan di latar belakang; saat nonaktif, key material kustom atau yang dicabut tetap diterima. Broken pool ditolak penuh.
