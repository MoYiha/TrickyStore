# Backup and Restore

**भाषा:** [English](../../../system/BackupRestore.md) | [Türkçe](../../tr/system/BackupRestore.md) | [简体中文](../../zh-CN/system/BackupRestore.md) | [Español](../../es/system/BackupRestore.md) | [Deutsch](../../de/system/BackupRestore.md) | [Русский](../../ru/system/BackupRestore.md) | [Bahasa Indonesia](../../id/system/BackupRestore.md) | **हिन्दी** | [العربية](../../ar/system/BackupRestore.md)

Config और authorized key material को authenticated encrypted archive में transfer करता है। Export कम से कम 12-char password और allowlist files उपयोग करता है; symlink/unknown path/excessive size reject होते हैं।

Import केवल encrypted CTSB स्वीकारता है और upload/entry/keybox/expanded size limits लागू करता है। Traversal, duplicate, directory, symlink target, malformed setting/keybox write से पहले reject होते हैं। Policy v2 full snapshot के रूप में validate होती है।
