# Boot Properties

**Язык:** [English](../../../identity/BootProperties.md) | [Türkçe](../../tr/identity/BootProperties.md) | [简体中文](../../zh-CN/identity/BootProperties.md) | [Español](../../es/identity/BootProperties.md) | [Deutsch](../../de/identity/BootProperties.md) | **Русский** | [Bahasa Indonesia](../../id/identity/BootProperties.md) | [हिन्दी](../../hi/identity/BootProperties.md) | [العربية](../../ar/identity/BootProperties.md)

Core userspace property view уменьшает видимость unlocked/debug/warranty/verified-boot/recovery indicators. Фиксированный набор применяется до Zygote и работает независимо от optional identity.

`boot_props_mode` относится только к Build Identity compatibility (`auto`, `force`, `disable`) и не выключает core protection. Он не relock bootloader, не repair verified boot и не меняет TEE root of trust.
