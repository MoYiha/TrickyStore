# Remote Sources

**Dil:** [English](../../../system/RemoteSources.md) | **Türkçe** | [简体中文](../../zh-CN/system/RemoteSources.md) | [Español](../../es/system/RemoteSources.md) | [Deutsch](../../de/system/RemoteSources.md) | [Русский](../../ru/system/RemoteSources.md) | [Bahasa Indonesia](../../id/system/RemoteSources.md) | [हिन्दी](../../hi/system/RemoteSources.md) | [العربية](../../ar/system/RemoteSources.md)

Remote Sources, açıkça yapılandırılmış HTTPS endpoint'ten authorized keybox material alır. Host, port, path, timeout, refresh interval, auth type/header ve response size sınırlandırılır; secret status response içinde gösterilmez.

Signed content zorunlu yapılabilir. Signature, XML/CBOX formatı, size, keybox, certificate ve revocation validation tamamlanmadan veri active olmaz. Failed refresh mevcut verified material'i bozuk download ile değiştirmez. Güvendiğiniz veya kontrol ettiğiniz endpoint kullanın.
