# Identity Refresh

**Idioma:** [English](../../../identity/IdentityRefresh.md) | [Türkçe](../../tr/identity/IdentityRefresh.md) | [简体中文](../../zh-CN/identity/IdentityRefresh.md) | **Español** | [Deutsch](../../de/identity/IdentityRefresh.md) | [Русский](../../ru/identity/IdentityRefresh.md) | [Bahasa Indonesia](../../id/identity/IdentityRefresh.md) | [हिन्दी](../../hi/identity/IdentityRefresh.md) | [العربية](../../ar/identity/IdentityRefresh.md)

Prepara una identidad validada para el siguiente boot sin cambiar el snapshot activo. Early boot valida path, tipo, tamaño, permisos y controles del staged file, lo promueve atómicamente y Build properties y service cargan el mismo snapshot.

IMEI/ICCID conservan checksums válidos y longitudes/caracteres están acotados. Una edición manual elimina un snapshot staged viejo; desactivar Spoof Engine o Refresh antes del boot impide una promoción no deseada.
