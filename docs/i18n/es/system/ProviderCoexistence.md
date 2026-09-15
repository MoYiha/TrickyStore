# Provider Coexistence

**Idioma:** [English](../../../system/ProviderCoexistence.md) | [Türkçe](../../tr/system/ProviderCoexistence.md) | [简体中文](../../zh-CN/system/ProviderCoexistence.md) | **Español** | [Deutsch](../../de/system/ProviderCoexistence.md) | [Русский](../../ru/system/ProviderCoexistence.md) | [Bahasa Indonesia](../../id/system/ProviderCoexistence.md) | [हिन्दी](../../hi/system/ProviderCoexistence.md) | [العربية](../../ar/system/ProviderCoexistence.md)

Automatic Build Identity detecta otros providers de fingerprint/property, incluyendo variantes PIF, `autopif`/`auto_pif` y PlayCurl, y evita sobrescribirlos.

Con conflicto, las Build properties opcionales quedan intactas mientras otras funciones pueden seguir activas. Force salta la detección de forma intencional; automatic es lo recomendado.
