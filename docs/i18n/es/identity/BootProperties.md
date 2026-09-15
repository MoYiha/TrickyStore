# Boot Properties

**Idioma:** [English](../../../identity/BootProperties.md) | [Türkçe](../../tr/identity/BootProperties.md) | [简体中文](../../zh-CN/identity/BootProperties.md) | **Español** | [Deutsch](../../de/identity/BootProperties.md) | [Русский](../../ru/identity/BootProperties.md) | [Bahasa Indonesia](../../id/identity/BootProperties.md) | [हिन्दी](../../hi/identity/BootProperties.md) | [العربية](../../ar/identity/BootProperties.md)

Es la vista userspace principal que reduce la exposición de indicadores comunes de unlocked/debug/warranty/verified boot/recovery. El conjunto de properties es fijo y se aplica antes de Zygote mientras el módulo y su early boot están operativos.

`boot_props_mode` solo controla compatibilidad opcional de Build Identity con `auto`, `force` o `disable`; no apaga la protección core. No relockea físicamente bootloader, no repara verified boot ni cambia TEE root of trust.
