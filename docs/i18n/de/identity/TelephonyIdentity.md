# Telephony Identity

**Sprache:** [English](../../../identity/TelephonyIdentity.md) | [Türkçe](../../tr/identity/TelephonyIdentity.md) | [简体中文](../../zh-CN/identity/TelephonyIdentity.md) | [Español](../../es/identity/TelephonyIdentity.md) | **Deutsch** | [Русский](../../ru/identity/TelephonyIdentity.md) | [Bahasa Indonesia](../../id/identity/TelephonyIdentity.md) | [हिन्दी](../../hi/identity/TelephonyIdentity.md) | [العربية](../../ar/identity/TelephonyIdentity.md)

Kann IMEI, MEID, IMSI, ICCID und Telefonnummer über unterstützte Binder APIs für zwei SIM-Slots präsentieren. Checksums, Längen, Syntax, Slot und Input Size werden validiert.

Zuerst wird die echte Android-Antwort abgefragt; Permission Denial, Error oder Null bleiben erhalten. Modem, Baseband, EFS, SIM und Carrier-Identität ändern sich nicht.
