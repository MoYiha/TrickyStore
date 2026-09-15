# Automatic Keybox Check

**भाषा:** [English](../../../security/AutomaticKeyboxCheck.md) | [Türkçe](../../tr/security/AutomaticKeyboxCheck.md) | [简体中文](../../zh-CN/security/AutomaticKeyboxCheck.md) | [Español](../../es/security/AutomaticKeyboxCheck.md) | [Deutsch](../../de/security/AutomaticKeyboxCheck.md) | [Русский](../../ru/security/AutomaticKeyboxCheck.md) | [Bahasa Indonesia](../../id/security/AutomaticKeyboxCheck.md) | **हिन्दी** | [العربية](../../ar/security/AutomaticKeyboxCheck.md)

Continuous storage scan के बिना keybox/revocation update रखता है। File observer normal changes संभालता है और जरूरत पर low-frequency fallback चलता है।

हर refresh key, chain, algorithm, validity, ambiguity, revocation verify करता है। मान्य keybox boot पर और offline environment में बिना network wait के तुरंत activate हो जाता है। Revocation check पूरी तरह Automatic Keybox Check पर निर्भर है: सक्षम होने पर network मिलने पर background में जाँच होती है और revoked keys हटा दी जाती हैं; अक्षम होने पर custom या revoked keybox बिना रुकावट इस्तेमाल किए जा सकते हैं। Cache file count/size bounded है।
