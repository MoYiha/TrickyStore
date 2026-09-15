# Encrypted Storage

**اللغة:** [English](../../../security/EncryptedStorage.md) | [Türkçe](../../tr/security/EncryptedStorage.md) | [简体中文](../../zh-CN/security/EncryptedStorage.md) | [Español](../../es/security/EncryptedStorage.md) | [Deutsch](../../de/security/EncryptedStorage.md) | [Русский](../../ru/security/EncryptedStorage.md) | [Bahasa Indonesia](../../id/security/EncryptedStorage.md) | [हिन्दी](../../hi/security/EncryptedStorage.md) | **العربية**

CBOX يستخدم authenticated AES-256-GCM ويربط metadata بـ ciphertext. Password containers تستخدم bounded key derivation، ومفتاح local protected cache داخل private config.

Unlock يقبل فقط عبر native WebUI ولا يتجاوز keybox verification. يتم إعادة فحص key/certificate/chain/date/algorithm/revocation. Hostile root يمكنه قراءة البيانات بعد unlock.
