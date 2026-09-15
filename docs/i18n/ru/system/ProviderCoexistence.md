# Provider Coexistence

**Язык:** [English](../../../system/ProviderCoexistence.md) | [Türkçe](../../tr/system/ProviderCoexistence.md) | [简体中文](../../zh-CN/system/ProviderCoexistence.md) | [Español](../../es/system/ProviderCoexistence.md) | [Deutsch](../../de/system/ProviderCoexistence.md) | **Русский** | [Bahasa Indonesia](../../id/system/ProviderCoexistence.md) | [हिन्दी](../../hi/system/ProviderCoexistence.md) | [العربية](../../ar/system/ProviderCoexistence.md)

Automatic Build Identity обнаруживает другие fingerprint/property providers, включая PIF, `autopif`/`auto_pif`, PlayCurl, и не перезаписывает их.

При конфликте optional Build properties untouched, остальные функции могут работать. Force намеренно bypass detection; Automatic рекомендуется.
