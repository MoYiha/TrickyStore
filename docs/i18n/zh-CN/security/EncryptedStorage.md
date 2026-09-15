# Encrypted Storage

**语言:** [English](../../../security/EncryptedStorage.md) | [Türkçe](../../tr/security/EncryptedStorage.md) | **简体中文** | [Español](../../es/security/EncryptedStorage.md) | [Deutsch](../../de/security/EncryptedStorage.md) | [Русский](../../ru/security/EncryptedStorage.md) | [Bahasa Indonesia](../../id/security/EncryptedStorage.md) | [हिन्दी](../../hi/security/EncryptedStorage.md) | [العربية](../../ar/security/EncryptedStorage.md)

CBOX 使用 authenticated AES-256-GCM 存储和传输 keybox，并通过 authentication data 绑定 metadata。密码型容器使用有界 key derivation，本地 protected cache key 位于私有配置区域。

Unlock 只通过原生 module-manager WebUI transport 接受。解密成功不能绕过 keybox verification，仍需检查 private key、certificate、chain、date、algorithm 与 revocation。Root compromise 后已解锁数据仍可能被读取。
