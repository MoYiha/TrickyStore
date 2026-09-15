# Patch Levels

**Sprache:** [English](../../../identity/PatchLevels.md) | [Türkçe](../../tr/identity/PatchLevels.md) | [简体中文](../../zh-CN/identity/PatchLevels.md) | [Español](../../es/identity/PatchLevels.md) | **Deutsch** | [Русский](../../ru/identity/PatchLevels.md) | [Bahasa Indonesia](../../id/identity/PatchLevels.md) | [हिन्दी](../../hi/identity/PatchLevels.md) | [العربية](../../ar/identity/PatchLevels.md)

`security_patch.txt` bietet System/Vendor/Boot-Regeln global und per App. Unterstützt werden Kalenderwerte, `today`, `device_default`, `prop`, `no`; Policy v2 hat Device, Property, Manual, Automatic, Omit getrennt je Komponente.

Parsing ist streng begrenzt und invalides Input ersetzt den Zustand nicht teilweise. Automatic nutzt Kalenderarithmetik. Die Funktion installiert keine realen Sicherheitsupdates und garantiert keinen Remote-Verdict.
