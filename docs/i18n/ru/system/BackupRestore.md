# Backup and Restore

**Язык:** [English](../../../system/BackupRestore.md) | [Türkçe](../../tr/system/BackupRestore.md) | [简体中文](../../zh-CN/system/BackupRestore.md) | [Español](../../es/system/BackupRestore.md) | [Deutsch](../../de/system/BackupRestore.md) | **Русский** | [Bahasa Indonesia](../../id/system/BackupRestore.md) | [हिन्दी](../../hi/system/BackupRestore.md) | [العربية](../../ar/system/BackupRestore.md)

Переносит config и authorized key material в authenticated encrypted archive. Export требует пароль минимум 12 символов и allowlist файлов, отклоняя symlink/unknown path/excessive size.

Import принимает только encrypted CTSB и ограничивает upload, entries, keyboxes и expanded size. Traversal, duplicates, directories, symlink target, malformed settings и invalid keybox отклоняются до записи. Policy v2 публикуется единым validated snapshot.
