# Provider Coexistence

**Sprache:** [English](../../../system/ProviderCoexistence.md) | [Türkçe](../../tr/system/ProviderCoexistence.md) | [简体中文](../../zh-CN/system/ProviderCoexistence.md) | [Español](../../es/system/ProviderCoexistence.md) | **Deutsch** | [Русский](../../ru/system/ProviderCoexistence.md) | [Bahasa Indonesia](../../id/system/ProviderCoexistence.md) | [हिन्दी](../../hi/system/ProviderCoexistence.md) | [العربية](../../ar/system/ProviderCoexistence.md)

Automatic Build Identity erkennt andere Fingerprint/Property-Provider wie PIF, `autopif`/`auto_pif` und PlayCurl und überschreibt sie nicht.

Bei Konflikt bleiben optionale Build Properties untouched, andere CleveresTricky-Funktionen können weiterarbeiten. Force umgeht bewusst die Erkennung; Automatic ist empfohlen.
