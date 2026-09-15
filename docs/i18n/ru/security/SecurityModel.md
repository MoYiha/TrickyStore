# Security Model

**Язык:** [English](../../../security/SecurityModel.md) | [Türkçe](../../tr/security/SecurityModel.md) | [简体中文](../../zh-CN/security/SecurityModel.md) | [Español](../../es/security/SecurityModel.md) | [Deutsch](../../de/security/SecurityModel.md) | **Русский** | [Bahasa Indonesia](../../id/security/SecurityModel.md) | [हिन्दी](../../hi/security/SecurityModel.md) | [العربية](../../ar/security/SecurityModel.md)

Root service, OS, KernelSU/APatch, module files и authorized key material trusted. Apps, Binder, uploads, remote responses, config, archives, rules, templates, paths, network metadata untrusted.

Config root-owned, sensitive root-only, symlink rejected, writes atomic. Binder ABI и kernel-validated copies проверяются. Injector ограничивает symbol/process/library, WebUI не открывает TCP и использует strict native bridge. Hostile root вне полной защиты.
