# Telephony Identity

**语言:** [English](../../../identity/TelephonyIdentity.md) | [Türkçe](../../tr/identity/TelephonyIdentity.md) | **简体中文** | [Español](../../es/identity/TelephonyIdentity.md) | [Deutsch](../../de/identity/TelephonyIdentity.md) | [Русский](../../ru/identity/TelephonyIdentity.md) | [Bahasa Indonesia](../../id/identity/TelephonyIdentity.md) | [हिन्दी](../../hi/identity/TelephonyIdentity.md) | [العربية](../../ar/identity/TelephonyIdentity.md)

Telephony Identity 可在支持的 Android telephony Binder API 中替换 IMEI、MEID、IMSI、ICCID、phone number，并支持两个 SIM slot。校验 checksum、长度、hex/phone syntax、slot 和 input size。

Interceptor 先获取真实 Android response，权限拒绝、error 或 null 都保留，不会绕过权限。它只影响 app-facing 值，不修改 modem、baseband、EFS、实体 SIM 或运营商看到的身份。
