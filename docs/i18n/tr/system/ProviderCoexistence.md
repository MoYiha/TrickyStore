# Provider Coexistence

**Dil:** [English](../../../system/ProviderCoexistence.md) | **Türkçe** | [简体中文](../../zh-CN/system/ProviderCoexistence.md) | [Español](../../es/system/ProviderCoexistence.md) | [Deutsch](../../de/system/ProviderCoexistence.md) | [Русский](../../ru/system/ProviderCoexistence.md) | [Bahasa Indonesia](../../id/system/ProviderCoexistence.md) | [हिन्दी](../../hi/system/ProviderCoexistence.md) | [العربية](../../ar/system/ProviderCoexistence.md)

Automatic Build Identity, başka aktif modül fingerprint/product property layer'ını yönetiyorsa onu ezmemek için provider detection yapar. KernelSU/APatch enabled module directories içindeki yaygın PIF, auto_pif/autopif ve PlayCurl varyantları aynı normalize politikayla algılanır.

Conflict varsa automatic mode optional Build properties'i değiştirmez fakat attestation, keybox, patch, RKP, DRM ve telephony özellikleri çalışabilir. Force mode bilinçli olarak detection'ı bypass eder; günlük kullanım için automatic önerilir.
