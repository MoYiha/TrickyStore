# Telephony Identity

**Bahasa:** [English](../../../identity/TelephonyIdentity.md) | [Türkçe](../../tr/identity/TelephonyIdentity.md) | [简体中文](../../zh-CN/identity/TelephonyIdentity.md) | [Español](../../es/identity/TelephonyIdentity.md) | [Deutsch](../../de/identity/TelephonyIdentity.md) | [Русский](../../ru/identity/TelephonyIdentity.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/identity/TelephonyIdentity.md) | [العربية](../../ar/identity/TelephonyIdentity.md)

Dapat menampilkan IMEI, MEID, IMSI, ICCID dan phone untuk dua SIM lewat supported Binder APIs. Checksum, length, syntax, slot dan input size divalidasi.

Genuine Android response diambil dulu; permission denial/error/null dipertahankan. Modem, baseband, EFS, physical SIM dan carrier identity tidak berubah.
