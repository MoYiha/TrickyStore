# Application Rules

**Sprache:** [English](../../../identity/ApplicationRules.md) | [Türkçe](../../tr/identity/ApplicationRules.md) | [简体中文](../../zh-CN/identity/ApplicationRules.md) | [Español](../../es/identity/ApplicationRules.md) | **Deutsch** | [Русский](../../ru/identity/ApplicationRules.md) | [Bahasa Indonesia](../../id/identity/ApplicationRules.md) | [हिन्दी](../../hi/identity/ApplicationRules.md) | [العربية](../../ar/identity/ApplicationRules.md)

Application Rules weist einer geeigneten App ein Gerätetemplate, eine verifizierte lokale Keybox oder eine Datenschutzrichtlinie zu. Eine gültige Regel ist bereits ein explizites Target. `inherit` behält die globale Policy, `isolate` erzeugt stabile app-spezifische IMEI/IMSI/ICCID/MEID/Telefon/Serial/Attestation-Identifier und ein DRM-`deviceUniqueId`-Pseudonym, `redact` leert unterstützte Werte unter Erhalt von Android-Berechtigungsfehlern.

Attestation-Identity benötigt eine aktive verifizierte Keybox. DRM Identifier Isolation ist unabhängig von DRM Keystore Passthrough. Shared-UID-Pakete werden deterministisch über Package Manager aufgelöst; einem Paketnamen aus der Anfrage wird nicht vertraut. Regeln werden als begrenzter, atomar ersetzter Zustand mit Cache-Invalidierung gehalten.
