# Encrypted Storage

**भाषा:** [English](../../../security/EncryptedStorage.md) | [Türkçe](../../tr/security/EncryptedStorage.md) | [简体中文](../../zh-CN/security/EncryptedStorage.md) | [Español](../../es/security/EncryptedStorage.md) | [Deutsch](../../de/security/EncryptedStorage.md) | [Русский](../../ru/security/EncryptedStorage.md) | [Bahasa Indonesia](../../id/security/EncryptedStorage.md) | **हिन्दी** | [العربية](../../ar/security/EncryptedStorage.md)

CBOX authenticated AES-256-GCM उपयोग करता है और metadata को ciphertext से bind करता है। Password containers bounded key derivation उपयोग करते हैं; local protected cache key private config में रहती है।

Unlock केवल native WebUI से होता है और keybox verification bypass नहीं करता। Key/certificate/chain/date/algorithm/revocation फिर जांचे जाते हैं। Hostile root unlocked data पढ़ सकता है।
