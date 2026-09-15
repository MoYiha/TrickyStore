# Provider Coexistence

**语言:** [English](../../../system/ProviderCoexistence.md) | [Türkçe](../../tr/system/ProviderCoexistence.md) | **简体中文** | [Español](../../es/system/ProviderCoexistence.md) | [Deutsch](../../de/system/ProviderCoexistence.md) | [Русский](../../ru/system/ProviderCoexistence.md) | [Bahasa Indonesia](../../id/system/ProviderCoexistence.md) | [हिन्दी](../../hi/system/ProviderCoexistence.md) | [العربية](../../ar/system/ProviderCoexistence.md)

Automatic Build Identity 会检测其他已启用 fingerprint/property provider，例如常见 PIF、`autopif`/`auto_pif` 和 PlayCurl 变体，避免覆盖它们。

有冲突时 optional Build properties 保持不变，但 attestation、keybox、patch、RKP、DRM 和 telephony 仍可工作。Force mode 明确绕过检测，日常建议 automatic。
