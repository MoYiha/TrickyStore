# Keybox Manager

**Язык:** [English](../../../security/KeyboxManager.md) | [Türkçe](../../tr/security/KeyboxManager.md) | [简体中文](../../zh-CN/security/KeyboxManager.md) | [Español](../../es/security/KeyboxManager.md) | [Deutsch](../../de/security/KeyboxManager.md) | **Русский** | [Bahasa Indonesia](../../id/security/KeyboxManager.md) | [हिन्दी](../../hi/security/KeyboxManager.md) | [العربية](../../ar/security/KeyboxManager.md)

Загружает, проверяет, выбирает и мониторит authorized attestation material в legacy/XML/CBOX. Application Rules могут ссылаться на конкретный verified file; remote material untrusted до такой же локальной проверки.

Private key должен совпадать с leaf certificate; проверяются algorithm, chain, date, duplicate/ambiguity и revocation. Действительный материал ключей активируется при загрузке немедленно без ожидания сети. При включенном Automatic Keybox Check отзыв проверяется в фоне; при выключенном допускаются пользовательские или отозванные ключи. Broken pool отклоняется полностью.
