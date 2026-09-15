# Diagnostics

**Bahasa:** [English](../../../system/Diagnostics.md) | [Türkçe](../../tr/system/Diagnostics.md) | [简体中文](../../zh-CN/system/Diagnostics.md) | [Español](../../es/system/Diagnostics.md) | [Deutsch](../../de/system/Diagnostics.md) | [Русский](../../ru/system/Diagnostics.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/system/Diagnostics.md) | [العربية](../../ar/system/Diagnostics.md)

Periksa Dashboard untuk version, Engine, profile, keybox count, target size, RKP, DRM dan native features, lalu cari error pertama di Logs. Jika WebUI gagal, periksa logcat, daemon, `webroot`, architecture-specific `webui_bridge` dan manager state.

Copy Diagnostics di Info & Resources menyalin ringkasan dukungan terbatas dengan key berbahasa Inggris dan allowlist tetap. Ringkasan memuat version, root environment, native/interceptor state, aggregate keybox/rule count, process CPU/RSS dan feature flags; tidak memuat log, nama package/keybox, identity values, credentials, server configuration atau key material. Tinjau sebelum membagikannya karena feature flags tetap menjelaskan konfigurasi modul.

Untuk isolasi gunakan Minimal + reboot, verifikasi genuine path, lalu aktifkan fitur satu per satu. Effective State menampilkan rule/profile, scope, template, keybox ref, privacy, features, patches, RKP/DRM, KeyMint/StrongBox, provider coexistence dan reboot requirement tanpa private keys.
