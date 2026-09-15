# Boot Properties

**Sprache:** [English](../../../identity/BootProperties.md) | [Türkçe](../../tr/identity/BootProperties.md) | [简体中文](../../zh-CN/identity/BootProperties.md) | [Español](../../es/identity/BootProperties.md) | **Deutsch** | [Русский](../../ru/identity/BootProperties.md) | [Bahasa Indonesia](../../id/identity/BootProperties.md) | [हिन्दी](../../hi/identity/BootProperties.md) | [العربية](../../ar/identity/BootProperties.md)

Core Userspace-Property-Ansicht zur Reduktion typischer unlocked/debug/warranty/verified-boot/recovery-Indikatoren. Der feste Satz wird vor Zygote angewendet und bleibt unabhängig von optionaler Identität aktiv.

`boot_props_mode` steuert nur optionale Build-Identity-Kompatibilität (`auto`, `force`, `disable`) und deaktiviert nicht den Core-Schutz. Es sperrt keinen Bootloader physisch und repariert Verified Boot nicht.
