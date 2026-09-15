# Remote Sources

**Язык:** [English](../../../system/RemoteSources.md) | [Türkçe](../../tr/system/RemoteSources.md) | [简体中文](../../zh-CN/system/RemoteSources.md) | [Español](../../es/system/RemoteSources.md) | [Deutsch](../../de/system/RemoteSources.md) | **Русский** | [Bahasa Indonesia](../../id/system/RemoteSources.md) | [हिन्दी](../../hi/system/RemoteSources.md) | [العربية](../../ar/system/RemoteSources.md)

Получает authorized keybox только с явно настроенного HTTPS. Host/port/path/timeout/refresh/auth/header/size bounded, secrets не возвращаются в status.

Можно требовать signature. До signature, XML/CBOX, size, keybox, certificate и revocation validation ничего не активируется. Failed refresh не заменяет verified material.
