# Dukungan Magisk

**Bahasa:** [English](../../../system/Magisk.md) | [Türkçe](../../tr/system/Magisk.md) | [简体中文](../../zh-CN/system/Magisk.md) | [Español](../../es/system/Magisk.md) | [Deutsch](../../de/system/Magisk.md) | [Русский](../../ru/system/Magisk.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/system/Magisk.md) | [العربية](../../ar/system/Magisk.md)

## Pernyataan Dukungan

CleveresTricky secara resmi mendukung tiga lingkungan root, masing-masing dengan WebUI normal:

* **[KernelSU](https://kernelsu.org)** - WebUI dibuka dari tombol modul di aplikasi manajer.
* **[APatch](https://apatch.dev)** - WebUI dibuka dari tombol modul di aplikasi manajer.
* **Magisk** - WebUI dibuka dari tombol **Action** modul melalui aplikasi host WebUI (lihat di bawah).

> [!NOTE]
> Framework deteksi modern dan Google Play Integrity secara aktif memeriksa mount userspace dan biner root, sehingga solusi tingkat kernel dapat memberikan penyembunyian jangka panjang yang lebih kuat. Perbandingan arsitektur:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## WebUI di Magisk

Magisk tidak mengimplementasikan antarmuka WebUI modul, sehingga di Magisk tombol Action membuka WebUI CleveresTricky di dalam **[aplikasi host WebUI mandiri](https://github.com/adivenxnataly/KsuWebUI)**:

1. Di aplikasi Magisk, buka modul CleveresTricky lalu ketuk **Action**.
2. Pada penggunaan pertama, peluncur mengunduh APK host WebUI dari GitHub releases-nya dan menginstalnya. Unduhan hanya dilakukan sekali.
3. Peluncur kemudian membuka host dengan id modul `cleverestricky`, yang merujuk ke `/data/adb/modules/cleverestricky/webroot` (`index.html` beserta `bridge.js`, `policy.js`, dan `ux.js` yang sudah ada).

Terminologi, karena namanya membingungkan:

* **Aplikasi host WebUI** adalah aplikasi host/kontainer WebUI mandiri. Ini **bukan** KernelSU dan **tidak** menginstal manajer root apa pun; Magisk tetap menjadi satu-satunya solusi root di perangkat.
* **CleveresTricky WebUI** adalah antarmuka milik modul di `/data/adb/modules/cleverestricky/webroot`.
* **Backend root** adalah runtime native/Rust milik CleveresTricky (`cleverestrickyd`, backend, `webui_bridge`), tidak berubah di ketiga lingkungan. Aplikasi host hanyalah kontainer WebView dengan akses shell root; jalur komunikasi `bridge.js` ke `webui_bridge` yang sudah ada tidak diubah.

---

## Konfigurasi manual

Lebih suka mengedit file manual atau WebUI tidak dapat diakses? Lihat panduan mandiri [Konfigurasi Manual](ManualConfiguration.md). Arsip laporan jadi:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
Arsip diagnostik akan disimpan di `/data/adb/cleverestricky/bugreports/`.
