# Telephony Identity

**Dil:** [English](../../../identity/TelephonyIdentity.md) | **Türkçe** | [简体中文](../../zh-CN/identity/TelephonyIdentity.md) | [Español](../../es/identity/TelephonyIdentity.md) | [Deutsch](../../de/identity/TelephonyIdentity.md) | [Русский](../../ru/identity/TelephonyIdentity.md) | [Bahasa Indonesia](../../id/identity/TelephonyIdentity.md) | [हिन्दी](../../hi/identity/TelephonyIdentity.md) | [العربية](../../ar/identity/TelephonyIdentity.md)

Telephony Identity Android telephony Binder API'leri üzerinden selected app'lere dönen IMEI, MEID, IMSI, ICCID ve phone number değerlerini destekler; iki SIM slotu için ayrı değer kullanılabilir. IMEI/ICCID checksum, numeric/hex/phone syntax, slot ve input size validation uygulanır.

Interceptor önce genuine Android response'u alır. Android permission denied/null/error verirse bu karar korunur; modül uygulamaya okuyamadığı identifier için yeni yetki kazandırmaz. Değerler yalnız app-facing'dir ve modem, baseband, EFS, fiziksel SIM veya network operator kimliğini değiştirmez.
