# Patch Levels

**اللغة:** [English](../../../identity/PatchLevels.md) | [Türkçe](../../tr/identity/PatchLevels.md) | [简体中文](../../zh-CN/identity/PatchLevels.md) | [Español](../../es/identity/PatchLevels.md) | [Deutsch](../../de/identity/PatchLevels.md) | [Русский](../../ru/identity/PatchLevels.md) | [Bahasa Indonesia](../../id/identity/PatchLevels.md) | [हिन्दी](../../hi/identity/PatchLevels.md) | **العربية**

`security_patch.txt` يوفر System/Vendor/Boot global/per-app rules. يدعم dates و`today` و`device_default` و`prop` و`no`؛ policy v2 تدعم Device وProperty وManual وAutomatic وOmit بشكل مستقل.

Parsing محدود وinvalid input لا يطبق partial state. Automatic يستخدم calendar arithmetic. الميزة لا تثبت security update حقيقية ولا تغير firmware ولا تضمن remote verdict.
