# Application Rules

**Bahasa:** [English](../../../identity/ApplicationRules.md) | [Türkçe](../../tr/identity/ApplicationRules.md) | [简体中文](../../zh-CN/identity/ApplicationRules.md) | [Español](../../es/identity/ApplicationRules.md) | [Deutsch](../../de/identity/ApplicationRules.md) | [Русский](../../ru/identity/ApplicationRules.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/identity/ApplicationRules.md) | [العربية](../../ar/identity/ApplicationRules.md)

Menetapkan template, keybox lokal terverifikasi, atau privacy policy ke aplikasi yang memenuhi syarat. Rule valid sudah menjadi target eksplisit. `inherit` mempertahankan policy global, `isolate` menghasilkan IMEI/IMSI/ICCID/MEID/phone/serial/attestation identifiers dan pseudonim DRM `deviceUniqueId` yang stabil per aplikasi, sedangkan `redact` mengosongkan identifier yang didukung sambil mempertahankan permission failure Android.

Attestation Identity memerlukan keybox aktif yang valid. DRM isolation independen dari DRM Keystore Passthrough. Shared UID diselesaikan secara deterministik lewat Package Manager dan nama paket dari request tidak dipercaya. State baru dipublikasikan atomik dan cache terkait dibersihkan.
