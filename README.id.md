# CleveresTricky

**Bahasa:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | **Bahasa Indonesia** | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
[![Localization](https://img.shields.io/badge/Localization-9%20Languages-teal?logo=googletranslate)](LANGUAGES.md)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/cleverestech)
[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-orange.svg?logo=gnu)](LICENSING.md)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)
![Architecture](https://img.shields.io/badge/Arch-ARM64%20%7C%20x86--64-0969DA)

CleveresTricky adalah modul KernelSU dan APatch untuk Android Keystore, attestation, identitas, dan kompatibilitas aplikasi. Modul ini menggabungkan runtime native yang terkontrol dengan WebUI seluler agar pengguna dapat mengelola cakupan aplikasi, material kunci, identitas, level patch, perlindungan Remote Key Provisioning, dan kompatibilitas DRM dari satu tempat.

> Ini adalah dokumentasi pengguna yang dilokalkan. Jika ada perbedaan teknis, dokumentasi bahasa Inggris adalah sumber kanonik.

## Kemampuan utama

### Kontrol runtime

[Spoof Engine](docs/i18n/id.md#spoof-engine) mengontrol penggantian identitas opsional. Jalur inti Keystore, TEE, dan perlindungan boot property tetap aktif secara independen selama layanan modul sehat.

[Application Scope](docs/i18n/id.md#application-scope) menjelaskan targeted mode, global mode, aturan paket, UID Android bersama, dan pembaruan cache live.

[Application Rules](docs/i18n/id.md#application-rules) menjelaskan template khusus aplikasi, pemilihan keybox, dan identitas privasi stabil.

[Profiles](docs/i18n/id.md#profiles) menjelaskan Daily Compatibility, Default, Maximum Compatibility, dan Minimal.

### Attestation dan identitas

[Attestation](docs/i18n/id.md#attestation) menjelaskan penggantian rantai sertifikat, operasi KeyMint asli, StrongBox, dan batas kompatibilitas berbasis software.

[Certificate Safe Mode](docs/i18n/id.md#certificate-safe-mode) mendokumentasikan konsep konfigurasi lama. Targeting inti saat ini tidak lagi bergantung pada toggle tersebut.

[Keybox Manager](docs/i18n/id.md#keybox-manager) mencakup pemuatan, verifikasi, pemilihan, rotasi, pemeriksaan revocation, dan pemantauan keybox.

[Automatic Keybox Check](docs/i18n/id.md#automatic-keybox-check) menjelaskan worker pemeliharaan yang dibatasi dan lifecycle-nya.

[Remote Sources](docs/i18n/id.md#remote-sources) menjelaskan pengambilan terautentikasi, verifikasi signature, kebijakan refresh, dan perilaku kegagalan.

[Encrypted Storage](docs/i18n/id.md#encrypted-storage) menjelaskan container CBOX, cache lokal terlindungi, dan penanganan material kunci yang aman.

[Patch Levels](docs/i18n/id.md#patch-levels) menjelaskan field patch System, Vendor, dan Boot dengan aturan global dan per aplikasi.

[Build Identity](docs/i18n/id.md#build-identity) menjelaskan template perangkat, fingerprint, field Build yang terlihat aplikasi, aktivasi early boot tersinkronisasi, dan helper Pixel beta Auto Identity untuk pengguna Custom ROM.

[Identity Refresh](docs/i18n/id.md#identity-refresh) menjelaskan identitas untuk boot berikutnya dan konsistensi snapshot.

[Telephony Identity](docs/i18n/id.md#telephony-identity) menjelaskan nilai dual SIM, preservasi keputusan izin Android, API yang didukung, dan batas operator.

### Kompatibilitas platform

[Boot Properties](docs/i18n/id.md#boot-properties) menjelaskan tampilan userspace boot property inti dan kebijakan kompatibilitas identitas yang terpisah.

[Region Properties](docs/i18n/id.md#region-properties) menjelaskan tampilan negara dan wilayah hardware opsional yang dibatasi.

[Provider Coexistence](docs/i18n/id.md#provider-coexistence) menjelaskan bagaimana automatic mode menghindari menimpa fingerprint provider lain.

[RKP Protection](docs/i18n/id.md#rkp-protection) menjelaskan infrastruktur Android yang dilindungi dan generated-key passthrough asli.

[DRM Passthrough and Privacy](docs/i18n/id.md#drm-passthrough) memisahkan dua fungsi. Aplikasi media yang dipilih dapat tetap berada pada jalur sertifikat Keystore asli Android, sementara `privacy=isolate` dapat mengganti `deviceUniqueId` DRM modern yang didukung dengan pseudonim stabil khusus aplikasi.

Fitur ini bukan bypass Widevine atau DRM. Security level, lisensi, provisioning, content key, session, HDCP, dan string property tidak diubah.

### Antarmuka dan operasi

[Web Interface](docs/i18n/id.md#web-interface) menjelaskan transport native module manager, navigasi seluler, live status, validasi, dan aksesibilitas.

Bahasa WebUI bawaan adalah **English**, **Türkçe**, **简体中文**, **Español**, **Deutsch**, **Русский**, **Bahasa Indonesia**, **हिन्दी**, dan **العربية**. Semua katalog tersedia lokal sehingga mengganti bahasa tidak memerlukan jaringan.

[Backup and Restore](docs/i18n/id.md#backup-restore) menjelaskan export terenkripsi, import yang dibatasi, dan recovery aman.

[Installer](docs/i18n/id.md#installer) menjelaskan layout paket KernelSU/APatch, verifikasi payload, perangkat yang didukung, dan alur instalasi.

[Diagnostics](docs/i18n/id.md#diagnostics) menjelaskan log, pemeriksaan status, masalah umum, dan urutan troubleshooting terkontrol.

### Referensi engineering

[Security Model](docs/i18n/id.md#security-model) mendokumentasikan trust boundary, file terlindungi, validasi input, dan kemampuan yang tidak diklaim modul.

[Performance](docs/i18n/id.md#performance) mendokumentasikan lifecycle hook, cache terbatas, pekerjaan background, CPU, dan memori.

[Building](docs/i18n/id.md#building) mendokumentasikan toolchain, tugas validasi, dan artifact hasil build.

[Native Architecture](docs/i18n/id.md#native-architecture) mendokumentasikan Rust injector, Rust native core, kebijakan bahasa, dan satu batas Android C++ ABI yang diperlukan.

## Mulai cepat

1. Unduh ZIP release terbaru dari halaman Release resmi.
2. Jika perlu memastikan asal build resmi, verifikasi `SHA256SUMS` dan GitHub build provenance.
3. Buka KernelSU atau APatch saat Android berjalan.
4. Instal ZIP lalu reboot.
5. Buka CleveresTricky WebUI dari module manager.
6. Instalasi baru dimulai dengan Global Mode aktif dan identity spoofing opsional nonaktif.
7. Tambahkan hanya material kunci yang Anda miliki atau berhak Anda uji.
8. Konfigurasikan opsi identitas hanya saat diperlukan.
9. Reboot setelah mengubah nilai template Build Identity.

Tidak ada keybox yang dapat digunakan atau private attestation key yang dibundel di project atau release.

## Lingkungan yang didukung

CleveresTricky mendukung Android 12 hingga Android 17, API 31 hingga 37, ARM64 dan x86 64. Instalasi didukung melalui KernelSU atau APatch saat Android aktif.

Magisk dan recovery tidak didukung. Installer menghentikan jalur yang tidak didukung sebelum meninggalkan modul parsial.

## Batas penting

Hasil bergantung pada kondisi perangkat asli, firmware, sertifikasi, material kunci, Google Play services, dan remote policy. CleveresTricky meningkatkan jalur kompatibilitas lokal tetapi tidak menjamin verdict remote tertentu.

Nilai telephony hanya terlihat lewat API aplikasi yang didukung. Modem, baseband, EFS, SIM fisik, dan identitas yang terlihat operator tidak diubah.

Pada Android modern, Android ID dibatasi berdasarkan identitas tanda tangan aplikasi, pengguna, dan perangkat di SettingsProvider. CleveresTricky tidak menampilkan kontrol Android ID global yang menyesatkan.

Versi kernel asli tidak berubah. Boot property view tidak mengunci bootloader secara fisik, memperbaiki verified boot, menulis ulang vbmeta, atau mengubah hardware root of trust.

Bootloader yang terbuka tidak otomatis berarti semua DRM tidak bekerja. Perilaku nyata bergantung pada perangkat, implementasi vendor, provisioning, security level, firmware, dan service policy. Fitur DRM saat ini berfokus pada privasi, bukan bypass.

Catatan SHA 256 internal mendeteksi payload yang hilang, berubah, ditambahkan, berupa link, atau tidak diharapkan dan mengaktifkan tamper lockdown bila validasi gagal. Keaslian release resmi ditopang oleh digest terpisah dan GitHub signed build provenance.

## Konfigurasi awal yang disarankan

Mulai dengan default instalasi baru. Global Mode memilih UID aplikasi yang memenuhi syarat sementara perlindungan boot dan Keystore inti tetap aktif. Identity spoofing opsional tetap nonaktif sampai Anda menyalakannya dari bagian Identity.

Untuk Custom ROM, Auto Identity dapat mengambil Pixel beta atau canary Build Identity dari metadata publik Google dan menyimpannya lokal. Aktifkan Identity Spoof Engine dan reboot hanya saat Anda memang ingin menampilkan nilai itu.

Untuk privasi DRM identifier, buat Application Rule untuk aplikasi media dan gunakan `privacy=isolate`. DRM Keystore Passthrough dapat tetap aktif karena jalur sertifikat Keystore asli dan pseudonim DRM ID adalah jalur terpisah.

## Bantuan dan informasi project

Untuk diagnosis gunakan WebUI Logs atau Android logcat dengan tag `CleveresTricky`. Detail ada di [Diagnostics](docs/i18n/id.md#diagnostics).

Riwayat project ada di [CHANGELOG](docs/i18n/id.md#changelog), panduan kontribusi di [Contributing](docs/i18n/id.md#contributing), bahasa di [Language Support](docs/i18n/id.md#languages), dan tema di [Theme](docs/i18n/id.md#theme).

[Dokumentasi Android attestation](https://source.android.com/docs/security/features/keystore/attestation), [Play Integrity verdict](https://developer.android.com/google/play/integrity/verdicts), dan [panduan modul KernelSU](https://kernelsu.org/guide/module.html) tetap menjadi referensi resmi platform masing-masing.

## Kontrol identitas opsional granular

CleveresTricky menyelesaikan Device and Build Identity, Attestation Identity, Telephony Identity, Region Identity, Identity Refresh, dan Security Patch secara terpisah. Security Patch independen dari Device and Build Identity.

Keystore interception inti, operasi private key KeyMint dan StrongBox asli, root of trust, Binder safety, dan boot compatibility tetap independen dari kontrol opsional tersebut. Antarmuka membedakan kondisi asli yang ditangkap, kondisi presentasi terkonfigurasi, dan kondisi efektif yang terlihat aplikasi.

Security Patch menyediakan policy terpisah untuk System, Vendor, dan Boot. Profiles dapat menetapkan opsi yang koheren ke aplikasi, dan Effective State inspector menampilkan hasil resolver runtime.
