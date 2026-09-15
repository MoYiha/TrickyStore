# Spoof Engine

**اللغة:** [English](../../../identity/SpoofEngine.md) | [Türkçe](../../tr/identity/SpoofEngine.md) | [简体中文](../../zh-CN/identity/SpoofEngine.md) | [Español](../../es/identity/SpoofEngine.md) | [Deutsch](../../de/identity/SpoofEngine.md) | [Русский](../../ru/identity/SpoofEngine.md) | [Bahasa Indonesia](../../id/identity/SpoofEngine.md) | [हिन्दी](../../hi/identity/SpoofEngine.md) | **العربية**

Optional app-facing identity controller. Core Keystore/TEE وcertificate compatibility وroot of trust وboot protection تبقى حتى عند إيقافه.

عند التشغيل تعمل optional Attestation/Telephony/Build/Region/Refresh حسب controls الخاصة بها. الإيقاف لا يحذف saved values. App cache قد يحتاج restart وBuild Identity يحتاج reboot.
