# Boot Properties

**语言:** [English](../../../identity/BootProperties.md) | [Türkçe](../../tr/identity/BootProperties.md) | **简体中文** | [Español](../../es/identity/BootProperties.md) | [Deutsch](../../de/identity/BootProperties.md) | [Русский](../../ru/identity/BootProperties.md) | [Bahasa Indonesia](../../id/identity/BootProperties.md) | [हिन्दी](../../hi/identity/BootProperties.md) | [العربية](../../ar/identity/BootProperties.md)

Boot Properties 是核心 userspace property 视图，用于减少应用读取常见 unlocked、debug、warranty、verified boot 和 recovery 标记时的暴露。固定 property 集在 Zygote 之前应用，并独立于可选身份功能保持启用。

`boot_props_mode` 只控制可选 template Build Identity compatibility，可为 `auto`、`force`、`disable`，不会关闭核心 boot property protection。该视图不会物理锁回 bootloader、修复 verified boot 或改变 TEE root of trust。
