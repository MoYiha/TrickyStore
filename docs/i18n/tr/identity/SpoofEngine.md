# Spoof Engine

**Dil:** [English](../../../identity/SpoofEngine.md) | **Türkçe** | [简体中文](../../zh-CN/identity/SpoofEngine.md) | [Español](../../es/identity/SpoofEngine.md) | [Deutsch](../../de/identity/SpoofEngine.md) | [Русский](../../ru/identity/SpoofEngine.md) | [Bahasa Indonesia](../../id/identity/SpoofEngine.md) | [हिन्दी](../../hi/identity/SpoofEngine.md) | [العربية](../../ar/identity/SpoofEngine.md)

Spoof Engine optional app-facing identity runtime control'dür. Core Keystore/TEE interception, certificate compatibility, root of trust handling ve boot protection motor kapalıyken de core path olarak devam eder.

Motor açıkken configured attestation identity, Telephony Identity, optional Build Identity, Region Identity ve Identity Refresh kendi dedicated controls ile çalışabilir. Motor kapatılınca identity values kaybolmaz, sadece interception path'lerde sunulmaz. Application cache nedeniyle live değişiklik sonrası app restart, Build Identity değişikliğinde reboot gerekebilir.
