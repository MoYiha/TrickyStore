# Spoof Engine

**Bahasa:** [English](../../../identity/SpoofEngine.md) | [Türkçe](../../tr/identity/SpoofEngine.md) | [简体中文](../../zh-CN/identity/SpoofEngine.md) | [Español](../../es/identity/SpoofEngine.md) | [Deutsch](../../de/identity/SpoofEngine.md) | [Русский](../../ru/identity/SpoofEngine.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/identity/SpoofEngine.md) | [العربية](../../ar/identity/SpoofEngine.md)

Optional app-facing identity controller. Core Keystore/TEE, certificate compatibility, root of trust dan boot protection tetap berjalan saat off.

Saat on, optional Attestation/Telephony/Build/Region/Refresh mengikuti controls masing-masing. Off tidak menghapus saved values. App cache mungkin perlu restart, Build Identity perlu reboot.
