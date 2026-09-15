# Application Rules

**भाषा:** [English](../../../identity/ApplicationRules.md) | [Türkçe](../../tr/identity/ApplicationRules.md) | [简体中文](../../zh-CN/identity/ApplicationRules.md) | [Español](../../es/identity/ApplicationRules.md) | [Deutsch](../../de/identity/ApplicationRules.md) | [Русский](../../ru/identity/ApplicationRules.md) | [Bahasa Indonesia](../../id/identity/ApplicationRules.md) | **हिन्दी** | [العربية](../../ar/identity/ApplicationRules.md)

Eligible app को template, verified local keybox या privacy policy assign करता है। Valid rule स्वयं explicit target है। `inherit` global policy रखता है, `isolate` protected random seed से stable app-scoped IMEI/IMSI/ICCID/MEID/phone/serial/attestation identifiers और DRM `deviceUniqueId` pseudonym बनाता है, `redact` supported identifiers blank करता है पर Android permission failures बचाता है।

Attestation Identity को active verified keybox चाहिए। DRM isolation, DRM Keystore Passthrough से independent है। Shared UID Package Manager के real package set से deterministic resolve होता है। State atomic snapshot के रूप में publish होती है और cache invalidate होती है।
