# Build Identity

**Dil:** [English](../../../identity/BuildIdentity.md) | **Türkçe** | [简体中文](../../zh-CN/identity/BuildIdentity.md) | [Español](../../es/identity/BuildIdentity.md) | [Deutsch](../../de/identity/BuildIdentity.md) | [Русский](../../ru/identity/BuildIdentity.md) | [Bahasa Indonesia](../../id/identity/BuildIdentity.md) | [हिन्दी](../../hi/identity/BuildIdentity.md) | [العربية](../../ar/identity/BuildIdentity.md)

Build Identity, tam cihaz şablonunu fingerprint ve desteklenen app-visible Build alanlarına uygular. Optional'dır, Spoof Engine gerektirir ve Android bu değerleri erken yakaladığı için değişiklik sonrası reboot gerekir. Template manufacturer, model, brand, product, device, fingerprint, release, build ID, incremental, type, tags ve security patch bilgisi taşır; arbitrary `ro.*` property kabul edilmez.

Auto Identity, Custom ROM kullanıcıları için Google public metadata üzerinden güncel Pixel beta/canary Build Identity elde edip yerel kaydeder; motoru otomatik açmaz. Identity Refresh yeni snapshot'ı bir sonraki boot için hazırlar. Build Identity, Security Patch, Region, Telephony ve Attestation Identity birbirinden bağımsız çözülür.
