# Remote Sources

**Sprache:** [English](../../../system/RemoteSources.md) | [Türkçe](../../tr/system/RemoteSources.md) | [简体中文](../../zh-CN/system/RemoteSources.md) | [Español](../../es/system/RemoteSources.md) | **Deutsch** | [Русский](../../ru/system/RemoteSources.md) | [Bahasa Indonesia](../../id/system/RemoteSources.md) | [हिन्दी](../../hi/system/RemoteSources.md) | [العربية](../../ar/system/RemoteSources.md)

Ruft autorisiertes Keybox-Material nur von explizitem HTTPS ab. Host/Port/Path/Timeout/Refresh/Auth/Header/Response Size sind begrenzt und Secrets fehlen in Statusantworten.

Signaturen können verlangt werden. Vor Signature-, XML/CBOX-, Size-, Keybox-, Certificate- und Revocation-Prüfung wird nichts aktiviert. Fehlgeschlagener Refresh ersetzt verifiziertes Material nicht.
