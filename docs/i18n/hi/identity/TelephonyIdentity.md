# Telephony Identity

**भाषा:** [English](../../../identity/TelephonyIdentity.md) | [Türkçe](../../tr/identity/TelephonyIdentity.md) | [简体中文](../../zh-CN/identity/TelephonyIdentity.md) | [Español](../../es/identity/TelephonyIdentity.md) | [Deutsch](../../de/identity/TelephonyIdentity.md) | [Русский](../../ru/identity/TelephonyIdentity.md) | [Bahasa Indonesia](../../id/identity/TelephonyIdentity.md) | **हिन्दी** | [العربية](../../ar/identity/TelephonyIdentity.md)

Supported Binder APIs में दो SIM slots के लिए IMEI, MEID, IMSI, ICCID, phone प्रस्तुत कर सकता है। Checksum, length, syntax, slot, input size validate होते हैं।

पहले genuine Android response लिया जाता है; permission denial/error/null preserve होते हैं। Modem, baseband, EFS, physical SIM, carrier identity नहीं बदलते।
