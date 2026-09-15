# Remote Sources

**Idioma:** [English](../../../system/RemoteSources.md) | [Türkçe](../../tr/system/RemoteSources.md) | [简体中文](../../zh-CN/system/RemoteSources.md) | **Español** | [Deutsch](../../de/system/RemoteSources.md) | [Русский](../../ru/system/RemoteSources.md) | [Bahasa Indonesia](../../id/system/RemoteSources.md) | [हिन्दी](../../hi/system/RemoteSources.md) | [العربية](../../ar/system/RemoteSources.md)

Obtiene keybox autorizado solo desde endpoint HTTPS explícito. Host, port, path, timeout, refresh, auth/header y response size están limitados; secrets no se muestran en status.

Se puede exigir firma. Ningún dato se activa antes de pasar signature, XML/CBOX, size, keybox, certificate y revocation validation. Un refresh fallido no reemplaza material previamente verificado.
