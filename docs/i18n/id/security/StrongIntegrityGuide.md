# Panduan Strong Integrity

**Bahasa:** [English](../../../security/StrongIntegrityGuide.md) | [Türkçe](../../tr/security/StrongIntegrityGuide.md) | [简体中文](../../zh-CN/security/StrongIntegrityGuide.md) | [Español](../../es/security/StrongIntegrityGuide.md) | [Deutsch](../../de/security/StrongIntegrityGuide.md) | [Русский](../../ru/security/StrongIntegrityGuide.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/StrongIntegrityGuide.md) | [العربية](../../ar/security/StrongIntegrityGuide.md)

Langkah cepat untuk memenuhi Google Play Integrity (`MEETS_STRONG_INTEGRITY`) menggunakan CleveresTricky berdasarkan jenis ROM:

- **ROM Resmi (Stock)**: Instal CleveresTricky dan tambahkan Keybox yang valid. Biasanya hanya itu yang diperlukan.
- **ROM Resmi dengan Patch Keamanan Lama**: Di WebUI Dashboard, aktifkan `Security Patch` dan atur ke `Auto`.
- **ROM AOSP**: Di WebUI Dashboard, aktifkan `Identity`, spoof fingerprint perangkat Anda (bisa menggunakan Auto Pixel Identity atau templat perangkat bersertifikat). *(Catatan: Identity dan spoofing otomatis dapat sedikit meningkatkan penggunaan RAM karena evaluasi properti dinamis).*
- **Custom ROM**: Custom ROM tidak didukung secara resmi. Jika Keystore rusak atau atestasi hardware native tidak berfungsi, gunakan metode lama.

**Alat dan Impor Keybox:**
- Keybox Checker Online: https://keybox.tryigit.dev/checker
- Unduh dan Info Keybox: https://keybox.tryigit.dev/
- Cara Impor: Buka CleveresTricky WebUI → Keybox Manager dan unggah file Anda. Tidak perlu menyalin manual ke direktori TrickyStore lama.

**Pemberitahuan Hukum & Komunitas:** CleveresTricky adalah proyek open-source independen yang didukung oleh komunitas CleveresTricky, tidak berafiliasi dengan Google LLC. Kebijakan dan deteksi Google Play Integrity dapat berubah sewaktu-waktu tanpa pemberitahuan; tidak ada jaminan hasil permanen. Gunakan hanya kunci yang sah.
