# Backup and Restore

**语言:** [English](../../../system/BackupRestore.md) | [Türkçe](../../tr/system/BackupRestore.md) | **简体中文** | [Español](../../es/system/BackupRestore.md) | [Deutsch](../../de/system/BackupRestore.md) | [Русский](../../ru/system/BackupRestore.md) | [Bahasa Indonesia](../../id/system/BackupRestore.md) | [हिन्दी](../../hi/system/BackupRestore.md) | [العربية](../../ar/system/BackupRestore.md)

Backup/Restore 使用一个 authenticated encrypted archive 迁移配置和授权 key material。Export 要求至少 12 字符密码，只从 allowlist 收集已知配置和普通 keybox 文件，并拒绝 symlink、未知路径、过多文件和超限大小。

Import 仅接受加密 CTSB，限制 upload、entry 数量、keybox 数量、单文件大小和总 expanded size。Traversal、重复名、目录、symlink destination、非法文本/设置/keybox 会在写入前全部拒绝。Version two policy 和 profile 引用也会验证并作为完整 snapshot 发布。
