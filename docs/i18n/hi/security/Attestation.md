# Attestation

**भाषा:** [English](../../../security/Attestation.md) | [Türkçe](../../tr/security/Attestation.md) | [简体中文](../../zh-CN/security/Attestation.md) | [Español](../../es/security/Attestation.md) | [Deutsch](../../de/security/Attestation.md) | [Русский](../../ru/security/Attestation.md) | [Bahasa Indonesia](../../id/security/Attestation.md) | **हिन्दी** | [العربية](../../ar/security/Attestation.md)

Attestation layer चुने हुए apps के लिए नियंत्रित certificate-chain compatibility देता है, जबकि Android की वास्तविक key creation और बाद की cryptographic operations बनी रहती हैं।

RKP infrastructure callers हमेशा Android के genuine provisioning path पर रहते हैं। Target app UID के लिए सफल `generateKey` replies और बाद की `getKeyEntry` certificate reads एक ही compatibility path का उपयोग करती हैं, ताकि एक alias अलग-अलग attestation leaf न दिखाए।

Private-key operation Android KeyMint या StrongBox ही करता है। Material active होने से पहले key/certificate match, algorithm, chain, validity, ambiguity और revocation जाँचे जाते हैं। Certificate substitution hardware root of trust नहीं बनाता, bootloader को physically lock नहीं करता और remote verdict की guarantee नहीं देता।
