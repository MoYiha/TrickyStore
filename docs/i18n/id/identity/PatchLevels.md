# Patch Levels

**Bahasa:** [English](../../../identity/PatchLevels.md) | [Türkçe](../../tr/identity/PatchLevels.md) | [简体中文](../../zh-CN/identity/PatchLevels.md) | [Español](../../es/identity/PatchLevels.md) | [Deutsch](../../de/identity/PatchLevels.md) | [Русский](../../ru/identity/PatchLevels.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/identity/PatchLevels.md) | [العربية](../../ar/identity/PatchLevels.md)

`security_patch.txt` menyediakan System/Vendor/Boot rules global/per-app. Mendukung date, `today`, `device_default`, `prop`, `no`; policy v2 punya Device, Property, Manual, Automatic, Omit independen.

Parsing bounded dan invalid input tidak menerapkan partial state. Automatic memakai calendar arithmetic. Fitur ini tidak memasang security update nyata, mengubah firmware atau menjamin remote verdict.
