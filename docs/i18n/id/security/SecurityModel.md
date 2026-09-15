# Security Model

**Bahasa:** [English](../../../security/SecurityModel.md) | [Türkçe](../../tr/security/SecurityModel.md) | [简体中文](../../zh-CN/security/SecurityModel.md) | [Español](../../es/security/SecurityModel.md) | [Deutsch](../../de/security/SecurityModel.md) | [Русский](../../ru/security/SecurityModel.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/security/SecurityModel.md) | [العربية](../../ar/security/SecurityModel.md)

Root service, OS, KernelSU/APatch, module files dan authorized key material trusted. Apps, Binder, uploads, remote response, config, archive, rules, templates, path dan network metadata untrusted.

Config root-owned, sensitive root-only, symlink rejected, writes atomic. Binder ABI/kernel validated copy diperiksa. Injector membatasi symbol/process/library, WebUI tidak membuka TCP dan memakai strict native bridge. Hostile root di luar perlindungan penuh.
