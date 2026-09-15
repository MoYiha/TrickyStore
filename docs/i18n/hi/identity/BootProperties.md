# Boot Properties

**भाषा:** [English](../../../identity/BootProperties.md) | [Türkçe](../../tr/identity/BootProperties.md) | [简体中文](../../zh-CN/identity/BootProperties.md) | [Español](../../es/identity/BootProperties.md) | [Deutsch](../../de/identity/BootProperties.md) | [Русский](../../ru/identity/BootProperties.md) | [Bahasa Indonesia](../../id/identity/BootProperties.md) | **हिन्दी** | [العربية](../../ar/identity/BootProperties.md)

Core userspace property view common unlocked/debug/warranty/verified-boot/recovery indicators की exposure कम करता है। Fixed set Zygote से पहले apply होता है और optional identity से independent active रहता है।

`boot_props_mode` केवल optional Build Identity compatibility (`auto`, `force`, `disable`) नियंत्रित करता है, core protection नहीं। यह bootloader physically relock, verified boot repair या TEE root of trust change नहीं करता।
