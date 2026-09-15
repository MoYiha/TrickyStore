# Region Properties

**语言:** [English](../../../identity/RegionProperties.md) | [Türkçe](../../tr/identity/RegionProperties.md) | **简体中文** | [Español](../../es/identity/RegionProperties.md) | [Deutsch](../../de/identity/RegionProperties.md) | [Русский](../../ru/identity/RegionProperties.md) | [Bahasa Indonesia](../../id/identity/RegionProperties.md) | [हिन्दी](../../hi/identity/RegionProperties.md) | [العربية](../../ar/identity/RegionProperties.md)

Region Properties 通过一小组固定 Android properties 提供可选、有界的 China-region 视图。Hardware/SIM/operator country、hardware level、radio marker 等值固定在代码中，不接受任意用户 property。

Spoof Engine 开启时在 Zygote 前应用。它不会改变真实 SIM 国家、radio registration、modem firmware、secure hardware sales region 或 carrier account。
