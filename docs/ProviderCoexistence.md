# Provider Coexistence

**Language:** **English** | [Türkçe](i18n/tr.md#provider-coexistence) | [简体中文](i18n/zh-CN.md#provider-coexistence) | [Español](i18n/es.md#provider-coexistence) | [Deutsch](i18n/de.md#provider-coexistence) | [Русский](i18n/ru.md#provider-coexistence) | [Bahasa Indonesia](i18n/id.md#provider-coexistence) | [हिन्दी](i18n/hi.md#provider-coexistence) | [العربية](i18n/ar.md#provider-coexistence)

## Purpose

Provider Coexistence prevents automatic Build Identity handling from overwriting another active module that already owns the fingerprint or related product fields.

## Detection

The early boot script and the runtime service inspect enabled module directories used by KernelSU and APatch. Disabled entries are ignored. Common Play Integrity providers, automatic PIF variants, PIF module identifiers, and PlayCurl variants are recognized with the same normalized matching policy.

Both compact `autopif` identifiers and underscored `auto_pif` identifiers are recognized. This keeps the runtime decision aligned with the early boot decision.

## Automatic behavior

When a conflict is detected in automatic mode, CleveresTricky leaves template Build properties untouched. It can still provide targeted attestation handling, keybox selection, patch rules, RKP protection, DRM passthrough, and supported telephony behavior.

This separation allows one module to own the device fingerprint without disabling unrelated CleveresTricky features.

## Force and disable modes

Force mode intentionally bypasses provider detection. Use it only when CleveresTricky should be the sole owner of the property layer and the interaction has been tested on that device.

Disable mode turns off CleveresTricky property handling while leaving the saved identity available for later use.

## Operational guidance

Use automatic mode for daily operation. If the fingerprint does not change, review the WebUI runtime status and logs before forcing the setting. Running two property owners at once can produce inconsistent values across processes and boot stages.

[Return to the project overview](../README.md)
