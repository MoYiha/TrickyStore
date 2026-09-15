# Identity Refresh

**اللغة:** [English](../../../identity/IdentityRefresh.md) | [Türkçe](../../tr/identity/IdentityRefresh.md) | [简体中文](../../zh-CN/identity/IdentityRefresh.md) | [Español](../../es/identity/IdentityRefresh.md) | [Deutsch](../../de/identity/IdentityRefresh.md) | [Русский](../../ru/identity/IdentityRefresh.md) | [Bahasa Indonesia](../../id/identity/IdentityRefresh.md) | [हिन्दी](../../hi/identity/IdentityRefresh.md) | **العربية**

يجهز validated identity للإقلاع التالي دون تغيير snapshot الحالية. Early boot يتحقق من staged file ثم يقوم atomic promotion كي تستخدم Build Properties والخدمة نفس الحالة.

IMEI/ICCID checksum والأطوال محدودة. Manual edit يحذف stage القديم؛ إيقاف Engine/Refresh قبل boot يمنع unwanted promotion.
