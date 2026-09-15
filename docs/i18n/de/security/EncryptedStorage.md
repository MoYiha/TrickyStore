# Encrypted Storage

**Sprache:** [English](../../../security/EncryptedStorage.md) | [Türkçe](../../tr/security/EncryptedStorage.md) | [简体中文](../../zh-CN/security/EncryptedStorage.md) | [Español](../../es/security/EncryptedStorage.md) | **Deutsch** | [Русский](../../ru/security/EncryptedStorage.md) | [Bahasa Indonesia](../../id/security/EncryptedStorage.md) | [हिन्दी](../../hi/security/EncryptedStorage.md) | [العربية](../../ar/security/EncryptedStorage.md)

CBOX verwendet authentifiziertes AES-256-GCM für Keybox-Speicherung/Transfer und bindet Metadata an Ciphertext. Passwortcontainer nutzen begrenzte Key Derivation, lokale Cache-Schlüssel liegen im privaten Konfigurationsbereich.

Unlock wird nur über native WebUI akzeptiert und ersetzt nicht die Keybox-Validierung. Key/Certificate/Chain/Date/Algorithm/Revocation werden erneut geprüft. Ein feindlicher Root-Prozess kann entsperrte Daten weiterhin lesen.
