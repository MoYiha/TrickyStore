# Security Model

**भाषा:** [English](../../../security/SecurityModel.md) | [Türkçe](../../tr/security/SecurityModel.md) | [简体中文](../../zh-CN/security/SecurityModel.md) | [Español](../../es/security/SecurityModel.md) | [Deutsch](../../de/security/SecurityModel.md) | [Русский](../../ru/security/SecurityModel.md) | [Bahasa Indonesia](../../id/security/SecurityModel.md) | **हिन्दी** | [العربية](../../ar/security/SecurityModel.md)

Root service, OS, KernelSU/APatch, module files और authorized key material trusted हैं। Apps, Binder, upload, remote response, config, archive, rule, template, path, network metadata untrusted हैं।

Config root-owned, sensitive root-only, symlink rejected, writes atomic। Binder ABI/kernel-validated copies verify होती हैं। Injector symbol/process/library restrict करता है, WebUI TCP नहीं खोलता और strict native bridge उपयोग करता है। Hostile root पूर्ण defense से बाहर है।
