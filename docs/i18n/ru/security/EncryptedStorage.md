# Encrypted Storage

**Язык:** [English](../../../security/EncryptedStorage.md) | [Türkçe](../../tr/security/EncryptedStorage.md) | [简体中文](../../zh-CN/security/EncryptedStorage.md) | [Español](../../es/security/EncryptedStorage.md) | [Deutsch](../../de/security/EncryptedStorage.md) | **Русский** | [Bahasa Indonesia](../../id/security/EncryptedStorage.md) | [हिन्दी](../../hi/security/EncryptedStorage.md) | [العربية](../../ar/security/EncryptedStorage.md)

CBOX использует authenticated AES-256-GCM и связывает metadata с ciphertext. Password containers используют bounded key derivation, локальный protected-cache key хранится в private config.

Unlock доступен только через native WebUI и не обходит keybox verification. Key/certificate/chain/date/algorithm/revocation проверяются снова. Hostile root может прочитать уже unlocked data.
