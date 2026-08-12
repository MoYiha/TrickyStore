# Telephony Identity

**Language:** **English** | [Türkçe](i18n/tr.md#telephony-identity) | [简体中文](i18n/zh-CN.md#telephony-identity) | [Español](i18n/es.md#telephony-identity) | [Deutsch](i18n/de.md#telephony-identity) | [Русский](i18n/ru.md#telephony-identity) | [Bahasa Indonesia](i18n/id.md#telephony-identity) | [हिन्दी](i18n/hi.md#telephony-identity) | [العربية](i18n/ar.md#telephony-identity)

## Purpose

Telephony Identity changes supported values returned to selected applications through Android telephony Binder APIs. It supports separate values for the first and second SIM slots.

## Supported values

The telephony interface can configure IMEI, MEID, IMSI, ICCID, and phone number responses. IMEI and ICCID values require valid checksums. Numeric lengths, hexadecimal MEID syntax, phone number syntax, slot index, and maximum input size are validated before storage.

Device serial belongs to the attestation identity configuration. It is not returned as a telephony Binder replacement and does not change the physical device serial.

If a secondary value is not configured, the primary value can be used for the second supported slot. Requests for an unsupported slot are left unchanged.

## Permission preservation

The interceptor first obtains the genuine Android response. An override is considered only after Android permits the request. If Android denies access, throws a permission result, or returns null, CleveresTricky preserves that decision.

This design does not grant an application access to an identifier it was not allowed to read. It also avoids inventing a value when the platform intentionally withholds one.

## Lifecycle

The telephony Binder interceptor starts only when Spoof Engine and Telephony Identity are both enabled. It unregisters when either control is disabled. An application may need to restart after a state change because applications can cache identifier results.

Refresh Identity on Boot prepares validated values as part of the same next boot identity snapshot used by build and attestation fields.

## Limits

These values are application facing only. The module does not modify the modem, baseband, EFS storage, physical SIM, subscription held by the carrier, or identity visible to a mobile network operator.

[Return to the project overview](../README.md)
