# Security Model

**اللغة:** [English](../../../security/SecurityModel.md) | [Türkçe](../../tr/security/SecurityModel.md) | [简体中文](../../zh-CN/security/SecurityModel.md) | [Español](../../es/security/SecurityModel.md) | [Deutsch](../../de/security/SecurityModel.md) | [Русский](../../ru/security/SecurityModel.md) | [Bahasa Indonesia](../../id/security/SecurityModel.md) | [हिन्दी](../../hi/security/SecurityModel.md) | **العربية**

Root service وOS وKernelSU/APatch وmodule files وauthorized key material trusted. Apps وBinder وuploads وremote responses وconfig وarchives وrules وtemplates وpaths وnetwork metadata untrusted.

Config root-owned وsensitive root-only وsymlink مرفوض وwrites atomic. Binder ABI وkernel-validated copies تتحقق. Injector يقيد symbol/process/library وWebUI لا يفتح TCP ويستخدم strict native bridge. Hostile root خارج دفاع كامل.
