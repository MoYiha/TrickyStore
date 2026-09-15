# Identity Refresh

**Dil:** [English](../../../identity/IdentityRefresh.md) | **Türkçe** | [简体中文](../../zh-CN/identity/IdentityRefresh.md) | [Español](../../es/identity/IdentityRefresh.md) | [Deutsch](../../de/identity/IdentityRefresh.md) | [Русский](../../ru/identity/IdentityRefresh.md) | [Bahasa Indonesia](../../id/identity/IdentityRefresh.md) | [हिन्दी](../../hi/identity/IdentityRefresh.md) | [العربية](../../ar/identity/IdentityRefresh.md)

Identity Refresh bir sonraki boot için yeni validated app-facing identity hazırlar; mevcut boot içindeki aktif snapshot'ı değiştirmez. Early boot sırasında staged file path, type, size, permission ve controls doğrulanır, ardından atomik olarak promote edilir ve hem Build properties hem service aynı snapshot'ı kullanır.

IMEI/ICCID checksum, numeric length ve serial charset sınırları korunur. Birden fazla template varsa yeni snapshot mümkün olduğunda farklı template seçer. Manual edit eski staged snapshot'ı siler; Spoof Engine veya Identity Refresh kapalıysa istenmeyen promotion yapılmaz.
