# Telephony Identity

**Idioma:** [English](../../../identity/TelephonyIdentity.md) | [Türkçe](../../tr/identity/TelephonyIdentity.md) | [简体中文](../../zh-CN/identity/TelephonyIdentity.md) | **Español** | [Deutsch](../../de/identity/TelephonyIdentity.md) | [Русский](../../ru/identity/TelephonyIdentity.md) | [Bahasa Indonesia](../../id/identity/TelephonyIdentity.md) | [हिन्दी](../../hi/identity/TelephonyIdentity.md) | [العربية](../../ar/identity/TelephonyIdentity.md)

Puede sustituir IMEI, MEID, IMSI, ICCID y teléfono en APIs Binder compatibles, con valores distintos para dos SIM. Se validan checksums, longitudes, sintaxis, slot y tamaño.

Primero se obtiene la respuesta genuina Android. Denegación de permisos, error o null se conservan, por lo que no se otorga acceso extra. Solo cambia la vista de apps, no modem, baseband, EFS, SIM física ni operador.
