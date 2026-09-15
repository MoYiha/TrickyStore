# Build Identity

**Idioma:** [English](../../../identity/BuildIdentity.md) | [Türkçe](../../tr/identity/BuildIdentity.md) | [简体中文](../../zh-CN/identity/BuildIdentity.md) | **Español** | [Deutsch](../../de/identity/BuildIdentity.md) | [Русский](../../ru/identity/BuildIdentity.md) | [Bahasa Indonesia](../../id/identity/BuildIdentity.md) | [हिन्दी](../../hi/identity/BuildIdentity.md) | [العربية](../../ar/identity/BuildIdentity.md)

Aplica una plantilla completa a fingerprint y campos Build visibles para apps. Es opcional, requiere Spoof Engine y reboot. La plantilla incluye manufacturer, model, brand, product, device, fingerprint, release, build ID, incremental, type, tags y security patch; properties arbitrarias se rechazan.

Auto Identity puede resolver un Pixel beta/canary desde metadata pública de Google y guardarlo, sin encender el motor automáticamente. Build Identity, Security Patch, Region, Telephony y Attestation Identity se resuelven por separado.
