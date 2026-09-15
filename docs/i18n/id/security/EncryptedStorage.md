# Encrypted Storage

**Bahasa:** [English](../../../security/EncryptedStorage.md) | [Türkçe](../../tr/security/EncryptedStorage.md) | [简体中文](../../zh-CN/security/EncryptedStorage.md) | [Español](../../es/security/EncryptedStorage.md) | [Deutsch](../../de/security/EncryptedStorage.md) | [Русский](../../ru/security/EncryptedStorage.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/EncryptedStorage.md) | [العربية](../../ar/security/EncryptedStorage.md)

CBOX memakai authenticated AES-256-GCM dan mengikat metadata ke ciphertext. Password container memakai bounded key derivation; local protected cache key ada di private config.

Unlock hanya lewat native WebUI dan tetap menjalankan keybox verification. Key/certificate/chain/date/algorithm/revocation diperiksa. Hostile root dapat membaca data setelah unlock.
