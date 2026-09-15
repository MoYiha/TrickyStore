# Automatic Keybox Check

**Bahasa:** [English](../../../security/AutomaticKeyboxCheck.md) | [Türkçe](../../tr/security/AutomaticKeyboxCheck.md) | [简体中文](../../zh-CN/security/AutomaticKeyboxCheck.md) | [Español](../../es/security/AutomaticKeyboxCheck.md) | [Deutsch](../../de/security/AutomaticKeyboxCheck.md) | [Русский](../../ru/security/AutomaticKeyboxCheck.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/AutomaticKeyboxCheck.md) | [العربية](../../ar/security/AutomaticKeyboxCheck.md)

Menjaga keybox/revocation tetap terbaru tanpa continuous storage scan. File observer menangani perubahan normal dan low-frequency fallback digunakan pada filesystem tertentu.

Setiap refresh mengulang verifikasi key, chain, algorithm, validity, ambiguity dan revocation. Keybox yang valid langsung aktif saat booting dan offline tanpa menunggu koneksi internet. Pemeriksaan revocation terikat langsung pada Automatic Keybox Check: saat aktif, revocation diverifikasi secara asinkron di latar belakang saat online dan kunci yang dicabut dihapus; saat nonaktif, keybox kustom atau yang dicabut tetap dapat digunakan. Cache dibatasi jumlah dan ukuran file.
