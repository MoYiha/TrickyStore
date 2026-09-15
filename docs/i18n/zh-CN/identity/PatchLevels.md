# Patch Levels

**语言:** [English](../../../identity/PatchLevels.md) | [Türkçe](../../tr/identity/PatchLevels.md) | **简体中文** | [Español](../../es/identity/PatchLevels.md) | [Deutsch](../../de/identity/PatchLevels.md) | [Русский](../../ru/identity/PatchLevels.md) | [Bahasa Indonesia](../../id/identity/PatchLevels.md) | [हिन्दी](../../hi/identity/PatchLevels.md) | [العربية](../../ar/identity/PatchLevels.md)

`security_patch.txt` 提供 System、Vendor、Boot 的 global/per-app attestation patch rules。支持 calendar、`today`、`device_default`、`prop`、`no`；version two 中每个组件独立支持 Device、Property、Manual、Automatic、Omit。

Parsing 对大小、section、package、field、date、value 做限制，非法文件不会部分更新运行状态。Automatic mode 使用日历逻辑。Patch presentation 不会实际安装安全更新、修改 kernel/vendor firmware 或保证远程判定。
