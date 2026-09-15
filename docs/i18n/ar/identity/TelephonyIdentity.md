# Telephony Identity

**اللغة:** [English](../../../identity/TelephonyIdentity.md) | [Türkçe](../../tr/identity/TelephonyIdentity.md) | [简体中文](../../zh-CN/identity/TelephonyIdentity.md) | [Español](../../es/identity/TelephonyIdentity.md) | [Deutsch](../../de/identity/TelephonyIdentity.md) | [Русский](../../ru/identity/TelephonyIdentity.md) | [Bahasa Indonesia](../../id/identity/TelephonyIdentity.md) | [हिन्दी](../../hi/identity/TelephonyIdentity.md) | **العربية**

يمكنه عرض IMEI وMEID وIMSI وICCID وphone لشريحتي SIM عبر supported Binder APIs. Checksum وlength وsyntax وslot وinput size تتحقق.

يتم أخذ genuine Android response أولا؛ permission denial/error/null تحفظ. لا يتغير modem أوbaseband أوEFS أوphysical SIM أوcarrier identity.
