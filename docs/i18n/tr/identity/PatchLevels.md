# Patch Levels

**Dil:** [English](../../../identity/PatchLevels.md) | **Türkçe** | [简体中文](../../zh-CN/identity/PatchLevels.md) | [Español](../../es/identity/PatchLevels.md) | [Deutsch](../../de/identity/PatchLevels.md) | [Русский](../../ru/identity/PatchLevels.md) | [Bahasa Indonesia](../../id/identity/PatchLevels.md) | [हिन्दी](../../hi/identity/PatchLevels.md) | [العربية](../../ar/identity/PatchLevels.md)

`security_patch.txt` System, Vendor ve Boot attestation patch alanları için global ve per-app kurallar sağlar. Calendar değerleri, `today`, `device_default`, `prop` ve `no` desteklenir; version two modelde her component için Device, Property, Manual, Automatic ve Omit modları bağımsız çözülür.

Parsing size/section/package/field/date/value sınırlarıyla tam replacement state üretir; invalid file running policy'yi kısmen değiştirmez. Automatic mode calendar arithmetic kullanır ve stale source için previous calendar month day five yaklaşımını uygular. Patch presentation gerçek security update kurmaz, kernel/vendor firmware düzeltmez ve remote verifier'ın diğer evidence'ını değiştirmez.
