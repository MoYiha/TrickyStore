# Keybox Manager

**اللغة:** [English](../../../security/KeyboxManager.md) | [Türkçe](../../tr/security/KeyboxManager.md) | [简体中文](../../zh-CN/security/KeyboxManager.md) | [Español](../../es/security/KeyboxManager.md) | [Deutsch](../../de/security/KeyboxManager.md) | [Русский](../../ru/security/KeyboxManager.md) | [Bahasa Indonesia](../../id/security/KeyboxManager.md) | [हिन्दी](../../hi/security/KeyboxManager.md) | **العربية**

يحمل ويتحقق ويختار ويراقب authorized attestation material بصيغ legacy/XML/CBOX. يمكن لـ Application Rule اختيار file محدد، وremote material يبقى untrusted حتى التحقق المحلي.

يجب أن يطابق private key الـ leaf certificate ويتم فحص algorithm وchain وdate وduplicate/ambiguity وrevocation. يتم تفعيل المفاتيح الصالحة فور الإقلاع دون انتظار الشبكة. عند تفعيل Automatic Keybox Check يتم فحص الإلغاء في الخلفية؛ وعند تعطيله يُسمح بالمواد المخصصة أو الملغاة. ويرفض broken pool بالكامل.
