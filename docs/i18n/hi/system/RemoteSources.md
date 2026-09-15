# Remote Sources

**भाषा:** [English](../../../system/RemoteSources.md) | [Türkçe](../../tr/system/RemoteSources.md) | [简体中文](../../zh-CN/system/RemoteSources.md) | [Español](../../es/system/RemoteSources.md) | [Deutsch](../../de/system/RemoteSources.md) | [Русский](../../ru/system/RemoteSources.md) | [Bahasa Indonesia](../../id/system/RemoteSources.md) | **हिन्दी** | [العربية](../../ar/system/RemoteSources.md)

Explicit HTTPS से authorized keybox लाता है। Host/port/path/timeout/refresh/auth/header/size bounded हैं और secrets status में नहीं।

Signature optional-required हो सकती है। Signature, XML/CBOX, size, keybox, certificate, revocation validation से पहले data active नहीं। Failed refresh verified material नहीं बदलता।
