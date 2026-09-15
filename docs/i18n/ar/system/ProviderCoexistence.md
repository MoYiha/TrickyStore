# Provider Coexistence

**اللغة:** [English](../../../system/ProviderCoexistence.md) | [Türkçe](../../tr/system/ProviderCoexistence.md) | [简体中文](../../zh-CN/system/ProviderCoexistence.md) | [Español](../../es/system/ProviderCoexistence.md) | [Deutsch](../../de/system/ProviderCoexistence.md) | [Русский](../../ru/system/ProviderCoexistence.md) | [Bahasa Indonesia](../../id/system/ProviderCoexistence.md) | [हिन्दी](../../hi/system/ProviderCoexistence.md) | **العربية**

Automatic Build Identity يكتشف fingerprint/property providers أخرى مثل PIF و`autopif`/`auto_pif` وPlayCurl ولا يكتب فوقها.

عند conflict تبقى optional Build properties كما هي ويمكن لبقية الميزات العمل. Force يتجاوز detection عمدا؛ Automatic موصى به.
