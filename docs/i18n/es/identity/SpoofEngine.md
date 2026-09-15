# Spoof Engine

**Idioma:** [English](../../../identity/SpoofEngine.md) | [Türkçe](../../tr/identity/SpoofEngine.md) | [简体中文](../../zh-CN/identity/SpoofEngine.md) | **Español** | [Deutsch](../../de/identity/SpoofEngine.md) | [Русский](../../ru/identity/SpoofEngine.md) | [Bahasa Indonesia](../../id/identity/SpoofEngine.md) | [हिन्दी](../../hi/identity/SpoofEngine.md) | [العربية](../../ar/identity/SpoofEngine.md)

Es el control de identidad opcional para apps. Core Keystore/TEE, certificate compatibility, root of trust y boot protection continúan incluso cuando está apagado.

Al activarlo pueden funcionar attestation identity, Telephony, Build Identity, Region e Identity Refresh según sus controles. Al apagarlo los valores guardados no se borran, solo dejan de presentarse. Apps pueden cachear valores y Build Identity requiere reboot.
