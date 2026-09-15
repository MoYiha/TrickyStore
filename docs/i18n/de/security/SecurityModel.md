# Security Model

**Sprache:** [English](../../../security/SecurityModel.md) | [Türkçe](../../tr/security/SecurityModel.md) | [简体中文](../../zh-CN/security/SecurityModel.md) | [Español](../../es/security/SecurityModel.md) | **Deutsch** | [Русский](../../ru/security/SecurityModel.md) | [Bahasa Indonesia](../../id/security/SecurityModel.md) | [हिन्दी](../../hi/security/SecurityModel.md) | [العربية](../../ar/security/SecurityModel.md)

Root Service, OS, KernelSU/APatch, installierte Moduldateien und autorisiertes Key Material sind trusted. Apps, Binder Input, Uploads, Remote Responses, Config, Archive, Regeln, Templates, Paths und Network Metadata sind untrusted.

Config muss root-owned sein, sensitive Dateien root-only, Symlinks werden abgewiesen, Writes sind atomar. Binder ABI und kernel-validierte Copies werden geprüft. Injector beschränkt Symbol/Prozess/Library und WebUI öffnet keinen TCP-Port. Ein feindlicher Root-Prozess liegt außerhalb vollständiger Abwehr.
