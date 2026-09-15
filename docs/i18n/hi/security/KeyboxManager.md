# Keybox Manager

**भाषा:** [English](../../../security/KeyboxManager.md) | [Türkçe](../../tr/security/KeyboxManager.md) | [简体中文](../../zh-CN/security/KeyboxManager.md) | [Español](../../es/security/KeyboxManager.md) | [Deutsch](../../de/security/KeyboxManager.md) | [Русский](../../ru/security/KeyboxManager.md) | [Bahasa Indonesia](../../id/security/KeyboxManager.md) | **हिन्दी** | [العربية](../../ar/security/KeyboxManager.md)

Authorized attestation material को legacy/XML/CBOX रूप में load, verify, select, monitor करता है। Application Rule specific file चुन सकता है; remote data local verification तक untrusted है।

Private key leaf certificate से match होना चाहिए; algorithm, chain, date, duplicate/ambiguity, revocation check होती है। मान्य key material boot के समय तुरंत सक्रिय हो जाता है। Automatic Keybox Check चालू होने पर revocation background में verify होता है; बंद होने पर custom/revoked material स्वीकार किया जाता है। Broken pool पूरा reject होता है।
