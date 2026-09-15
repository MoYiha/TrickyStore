# Remote Sources

**Bahasa:** [English](../../../system/RemoteSources.md) | [Türkçe](../../tr/system/RemoteSources.md) | [简体中文](../../zh-CN/system/RemoteSources.md) | [Español](../../es/system/RemoteSources.md) | [Deutsch](../../de/system/RemoteSources.md) | [Русский](../../ru/system/RemoteSources.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/system/RemoteSources.md) | [العربية](../../ar/system/RemoteSources.md)

Mengambil authorized keybox hanya dari HTTPS eksplisit. Host/port/path/timeout/refresh/auth/header/size dibatasi dan secret tidak muncul di status.

Signature dapat diwajibkan. Sebelum signature, XML/CBOX, size, keybox, certificate dan revocation validation selesai, data tidak aktif. Failed refresh tidak mengganti verified material.
