# Telephony Identity

**Язык:** [English](../../../identity/TelephonyIdentity.md) | [Türkçe](../../tr/identity/TelephonyIdentity.md) | [简体中文](../../zh-CN/identity/TelephonyIdentity.md) | [Español](../../es/identity/TelephonyIdentity.md) | [Deutsch](../../de/identity/TelephonyIdentity.md) | **Русский** | [Bahasa Indonesia](../../id/identity/TelephonyIdentity.md) | [हिन्दी](../../hi/identity/TelephonyIdentity.md) | [العربية](../../ar/identity/TelephonyIdentity.md)

Может представлять IMEI, MEID, IMSI, ICCID и phone для двух SIM через supported Binder APIs. Checksums, length, syntax, slot, input size валидируются.

Сначала получается genuine Android response; permission denial/error/null сохраняются. Modem, baseband, EFS, physical SIM и carrier identity не меняются.
