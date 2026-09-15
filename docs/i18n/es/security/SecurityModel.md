# Security Model

**Idioma:** [English](../../../security/SecurityModel.md) | [Türkçe](../../tr/security/SecurityModel.md) | [简体中文](../../zh-CN/security/SecurityModel.md) | **Español** | [Deutsch](../../de/security/SecurityModel.md) | [Русский](../../ru/security/SecurityModel.md) | [Bahasa Indonesia](../../id/security/SecurityModel.md) | [हिन्दी](../../hi/security/SecurityModel.md) | [العربية](../../ar/security/SecurityModel.md)

Root service, OS, KernelSU/APatch, module files y key material autorizado forman el trust boundary local. Apps, Binder input, uploads, remote responses, config, archives, rules, templates, paths y network metadata se tratan como untrusted.

Config debe ser root-owned, sensible root-only, sin symlinks y con writes atómicos. Binder ABI se valida antes de parsear copies kernel-validated. Injector restringe symbols/process/library y WebUI no abre TCP port, usando un bridge nativo con allowlists y bounds. Un root hostil queda fuera de una defensa total.
