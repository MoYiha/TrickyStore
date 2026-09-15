# Region Properties

**Dil:** [English](../../../identity/RegionProperties.md) | **Türkçe** | [简体中文](../../zh-CN/identity/RegionProperties.md) | [Español](../../es/identity/RegionProperties.md) | [Deutsch](../../de/identity/RegionProperties.md) | [Русский](../../ru/identity/RegionProperties.md) | [Bahasa Indonesia](../../id/identity/RegionProperties.md) | [हिन्दी](../../hi/identity/RegionProperties.md) | [العربية](../../ar/identity/RegionProperties.md)

Region Properties, küçük sabit bir Android property seti üzerinden optional bounded China-region görünümü sağlar. Hardware country, SIM country, operator country, hardware level ve radio compatibility marker gibi değerler code içinde sabittir ve arbitrary user input kabul edilmez.

Spoof Engine açıkken Zygote öncesi uygulanır. Bu kontrol gerçek SIM ülkesini, radio registration, modem firmware, secure hardware sales region veya carrier account'u değiştirmez. Vendor sorunu varsa kapatıp reboot edilmelidir.
