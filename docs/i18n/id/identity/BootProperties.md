# Boot Properties

**Bahasa:** [English](../../../identity/BootProperties.md) | [Türkçe](../../tr/identity/BootProperties.md) | [简体中文](../../zh-CN/identity/BootProperties.md) | [Español](../../es/identity/BootProperties.md) | [Deutsch](../../de/identity/BootProperties.md) | [Русский](../../ru/identity/BootProperties.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/identity/BootProperties.md) | [العربية](../../ar/identity/BootProperties.md)

Core userspace property view mengurangi exposure indikator unlocked/debug/warranty/verified boot/recovery. Set property fixed diterapkan sebelum Zygote dan tetap aktif independen dari optional identity.

`boot_props_mode` hanya mengontrol optional Build Identity compatibility (`auto`, `force`, `disable`), bukan core protection. Fitur ini tidak relock bootloader, memperbaiki verified boot atau mengubah TEE root of trust.
