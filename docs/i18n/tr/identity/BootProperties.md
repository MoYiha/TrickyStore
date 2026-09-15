# Boot Properties

**Dil:** [English](../../../identity/BootProperties.md) | **Türkçe** | [简体中文](../../zh-CN/identity/BootProperties.md) | [Español](../../es/identity/BootProperties.md) | [Deutsch](../../de/identity/BootProperties.md) | [Русский](../../ru/identity/BootProperties.md) | [Bahasa Indonesia](../../id/identity/BootProperties.md) | [हिन्दी](../../hi/identity/BootProperties.md) | [العربية](../../ar/identity/BootProperties.md)

Boot Properties, uygulamaların Android property üzerinden gördüğü yaygın unlocked/debug/warranty/verified boot/recovery göstergelerini sınırlayan core userspace görünümüdür. Core property seti Zygote öncesi uygulanır ve modül kurulu, early boot aracı kullanılabilir olduğu sürece aktif kalır.

`boot_props_mode` yalnız optional template Build Identity compatibility için `auto`, `force` veya `disable` kabul eder; bu seçeneklerin hiçbiri core boot property protection yolunu kapatmaz. Bu kullanıcı alanı görünümü bootloader'ı fiziksel olarak kilitlemez, verified boot'u onarmaz veya TEE root of trust'ı değiştirmez.
