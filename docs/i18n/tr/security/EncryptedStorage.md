# Encrypted Storage

**Dil:** [English](../../../security/EncryptedStorage.md) | **Türkçe** | [简体中文](../../zh-CN/security/EncryptedStorage.md) | [Español](../../es/security/EncryptedStorage.md) | [Deutsch](../../de/security/EncryptedStorage.md) | [Русский](../../ru/security/EncryptedStorage.md) | [Bahasa Indonesia](../../id/security/EncryptedStorage.md) | [हिन्दी](../../hi/security/EncryptedStorage.md) | [العربية](../../ar/security/EncryptedStorage.md)

CBOX keybox materyalini encrypted olarak saklama ve taşıma biçimidir. Authenticated AES-256-GCM kullanır; metadata authentication data ile ciphertext'e bağlanır. Password tabanlı container bounded key derivation kullanır, local protected cache anahtarı private config alanında tutulur.

Unlock yalnız native module-manager WebUI transport üzerinden kabul edilir. Başarılı decrypt, keybox verification'ı atlamaz; private key, certificate, chain, tarih, algoritma ve revocation yine kontrol edilir. Encryption yetkisiz key material'i meşru yapmaz ve root compromise durumunda unlocked veriyi koruyamaz.
