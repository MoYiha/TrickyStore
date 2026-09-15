# Spoof Engine

**Язык:** [English](../../../identity/SpoofEngine.md) | [Türkçe](../../tr/identity/SpoofEngine.md) | [简体中文](../../zh-CN/identity/SpoofEngine.md) | [Español](../../es/identity/SpoofEngine.md) | [Deutsch](../../de/identity/SpoofEngine.md) | **Русский** | [Bahasa Indonesia](../../id/identity/SpoofEngine.md) | [हिन्दी](../../hi/identity/SpoofEngine.md) | [العربية](../../ar/identity/SpoofEngine.md)

Optional app-facing identity controller. Core Keystore/TEE, certificate compatibility, root of trust и boot protection продолжаются даже когда он выключен.

При включении работают optional Attestation/Telephony/Build/Region/Refresh по своим controls. Выключение не удаляет saved values. App cache может потребовать restart, Build Identity reboot.
