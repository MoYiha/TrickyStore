# Riwayat Fitur & Pekerjaan Sebelumnya

Dokumen ini mencatat riwayat publik beberapa fitur utama CleveresTricky untuk pelacakan dan atribusi. Dokumen ini sendiri bukan klaim bahwa proyek lain menyalin kode sumber.

## Identitas perangkat & attestation

- **#79 — Konfigurasi per aplikasi dan penanganan `ATTESTATION_ID_*` (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
- **#139 — Identitas perangkat acak (2026-02-05)**
  https://github.com/tryigit/CleveresTricky/pull/139
- **#871 — Kontrol identitas perangkat/Dual-SIM berbasis aplikasi (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/871
  Mencakup IMEI, IMEI2, MEID, IMSI, ICCID, nomor telepon, dan Serial, beserta cakupan aplikasi/profil dan lifecycle runtime.

## Keybox / attestation

- **#77 — Manajemen dan rotasi multi-keybox (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/77
- **#79 — Verifikasi Keybox dan pekerjaan identitas attestation**
  https://github.com/tryigit/CleveresTricky/pull/79

## Arsitektur Native / Rust

- **#876 — Arsitektur interceptor Rust/Native dan lifecycle (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/876

## Fitur modul lainnya

Proyek ini juga mengembangkan profile/template, cakupan aplikasi, lifecycle hook, isolasi/redaction identitas, fitur terkait RKP/DRM, pengelolaan WebUI, serta integrasi StrongBox/attestation.

- #376 — https://github.com/tryigit/CleveresTricky/pull/376
- #476 — https://github.com/tryigit/CleveresTricky/pull/476
- #618 — https://github.com/tryigit/CleveresTricky/pull/618
- #908 — https://github.com/tryigit/CleveresTricky/pull/908
- #909 — https://github.com/tryigit/CleveresTricky/pull/909
- #910 — https://github.com/tryigit/CleveresTricky/pull/910
- #952 — https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — Pengalihan StrongBox ke TEE dan harmonisasi security level attestation**
  https://github.com/tryigit/CleveresTricky/pull/1132
  Perubahan ini kemudian di-revert dan karena itu tidak termasuk dalam `master` saat ini.

## Catatan historis

Tautan di atas adalah catatan pengembangan publik langsung dari GitHub. Kesamaan fungsi antarproyek dengan sendirinya tidak membuktikan penyalinan kode sumber atau pelanggaran lisensi.
