# Identity Refresh

**Bahasa:** [English](../../../identity/IdentityRefresh.md) | [Türkçe](../../tr/identity/IdentityRefresh.md) | [简体中文](../../zh-CN/identity/IdentityRefresh.md) | [Español](../../es/identity/IdentityRefresh.md) | [Deutsch](../../de/identity/IdentityRefresh.md) | [Русский](../../ru/identity/IdentityRefresh.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/identity/IdentityRefresh.md) | [العربية](../../ar/identity/IdentityRefresh.md)

Menyiapkan validated identity untuk boot berikutnya tanpa mengubah current snapshot. Early boot memvalidasi staged file dan atomically promotes sehingga Build Properties dan service menggunakan state sama.

IMEI/ICCID checksum dan length dibatasi. Manual edit membuang staged snapshot lama; Engine/Refresh off sebelum boot mencegah unwanted promotion.
