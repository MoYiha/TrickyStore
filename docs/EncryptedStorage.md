# Encrypted Storage

**Language:** **English** | [Türkçe](i18n/tr.md#encrypted-storage) | [简体中文](i18n/zh-CN.md#encrypted-storage) | [Español](i18n/es.md#encrypted-storage) | [Deutsch](i18n/de.md#encrypted-storage) | [Русский](i18n/ru.md#encrypted-storage) | [Bahasa Indonesia](i18n/id.md#encrypted-storage) | [हिन्दी](i18n/hi.md#encrypted-storage) | [العربية](i18n/ar.md#encrypted-storage)

## Purpose

CBOX provides encrypted storage and transfer for keybox material. It reduces exposure from plain XML while preserving the verification requirements of Keybox Manager.

## Container protection

CBOX uses authenticated AES 256 GCM encryption. Authentication data binds container metadata to the encrypted content, so modified data fails before key material is accepted.

Password based containers derive their encryption key through a bounded key derivation process. Device protected local caches use a key stored in the private configuration area. Sensitive byte arrays are cleared after use where the runtime allows it.

## Unlock behavior

Unlock requests are accepted only through the native module manager WebUI transport. The selected file name, password data, and optional public key information are validated. Successful decryption does not bypass keybox verification. The resulting material must still pass private key, certificate, chain, date, algorithm, and revocation checks.

Locked files remain visible as locked state without exposing their content. Unlocked material is tracked separately from server supplied material so the interface can report a clear source and status.

## Limits

Encryption does not make unauthorized key material legitimate. A running root process can access module state, and a compromised operating system can observe data after it is unlocked. Device security, root access control, and backup handling remain important.

Use a unique password for exported containers. Do not send a password through the same channel as the encrypted file when operational separation is required.

[Return to the project overview](../README.md)
