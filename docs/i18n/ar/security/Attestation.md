# Attestation

**اللغة:** [English](../../../security/Attestation.md) | [Türkçe](../../tr/security/Attestation.md) | [简体中文](../../zh-CN/security/Attestation.md) | [Español](../../es/security/Attestation.md) | [Deutsch](../../de/security/Attestation.md) | [Русский](../../ru/security/Attestation.md) | [Bahasa Indonesia](../../id/security/Attestation.md) | [हिन्दी](../../hi/security/Attestation.md) | **العربية**

توفر طبقة attestation توافقاً مضبوطاً لسلاسل الشهادات للتطبيقات المحددة مع إبقاء إنشاء المفاتيح الحقيقي في Android والعمليات التشفيرية اللاحقة كما هي.

تبقى نداءات بنية RKP دائماً على مسار provisioning الحقيقي في Android. وبالنسبة إلى UID التطبيقات المستهدفة، تستخدم ردود `generateKey` الناجحة وقراءات الشهادة اللاحقة عبر `getKeyEntry` مسار توافق واحداً كي لا يعرض alias واحد شهادتي attestation leaf مختلفتين.

تظل عملية المفتاح الخاص من تنفيذ Android KeyMint أو StrongBox. وقبل تفعيل المادة يتم التحقق من تطابق المفتاح والشهادة والخوارزمية والسلسلة والصلاحية والالتباس وحالة revocation. استبدال الشهادة لا ينشئ hardware root of trust ولا يقفل bootloader فعلياً ولا يضمن remote verdict.
