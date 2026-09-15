# Patch Levels

**Язык:** [English](../../../identity/PatchLevels.md) | [Türkçe](../../tr/identity/PatchLevels.md) | [简体中文](../../zh-CN/identity/PatchLevels.md) | [Español](../../es/identity/PatchLevels.md) | [Deutsch](../../de/identity/PatchLevels.md) | **Русский** | [Bahasa Indonesia](../../id/identity/PatchLevels.md) | [हिन्दी](../../hi/identity/PatchLevels.md) | [العربية](../../ar/identity/PatchLevels.md)

`security_patch.txt` задает System/Vendor/Boot global/per-app rules. Поддерживаются date, `today`, `device_default`, `prop`, `no`; policy v2 имеет независимые Device, Property, Manual, Automatic, Omit.

Parsing bounded и invalid input не публикует partial state. Automatic использует calendar arithmetic. Функция не устанавливает реальные security updates, не меняет kernel/vendor firmware и не гарантирует verdict.
