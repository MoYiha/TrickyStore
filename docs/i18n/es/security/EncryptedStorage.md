# Encrypted Storage

**Idioma:** [English](../../../security/EncryptedStorage.md) | [Türkçe](../../tr/security/EncryptedStorage.md) | [简体中文](../../zh-CN/security/EncryptedStorage.md) | **Español** | [Deutsch](../../de/security/EncryptedStorage.md) | [Русский](../../ru/security/EncryptedStorage.md) | [Bahasa Indonesia](../../id/security/EncryptedStorage.md) | [हिन्दी](../../hi/security/EncryptedStorage.md) | [العربية](../../ar/security/EncryptedStorage.md)

CBOX usa AES-256-GCM autenticado para guardar y transferir keyboxes. Metadata queda ligada al ciphertext y los containers con contraseña usan derivación acotada; la clave del cache local protegido vive en configuración privada.

Unlock solo se acepta por el transporte nativo de WebUI. Descifrar no evita la verificación de keybox: se repiten checks de private key, certificate, chain, date, algorithm y revocation. Un proceso root hostil puede leer datos una vez desbloqueados.
