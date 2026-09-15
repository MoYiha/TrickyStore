# RKP Protection

**اللغة:** [English](../../../security/RkpProtection.md) | [Türkçe](../../tr/security/RkpProtection.md) | [简体中文](../../zh-CN/security/RkpProtection.md) | [Español](../../es/security/RkpProtection.md) | [Deutsch](../../de/security/RkpProtection.md) | [Русский](../../ru/security/RkpProtection.md) | [Bahasa Indonesia](../../id/security/RkpProtection.md) | [हिन्दी](../../hi/security/RkpProtection.md) | **العربية**

تحافظ حماية Remote Key Provisioning على بنية provisioning في Android ضمن المسار الحقيقي للمنصة. تبقى حزم RKP الخاصة بـ Android/Google وحزم Remote Provisioner القديمة خارج نطاق الاستبدال، كما تفشل UID النظام وحالات تعذر حل الحزمة بوضع fail closed.

لا يتم تعديل نداءات بنية RKP أبداً. وبالنسبة إلى UID التطبيقات المستهدفة، تستخدم ردود `generateKey` وقراءات `getKeyEntry` اللاحقة مسار توافق شهادات موحداً لمنع alias واحد من إظهار attestation leaf مختلفتين.

تم تقاعد المفتاح القديم `rkp_passthrough`. يمكن أن تبقى العلامة في config أو backup قديم، لكنها لا تتحكم بعد الآن في generated-key ولا تظهر كـ runtime toggle في WebUI. لا تغير Profiles المدمجة سلوك RKP؛ فحماية البنية فعالة دائماً.

لا يحاكي CleveresTricky خادم RKP ولا ينشئ provisioning credentials ولا يغير hardware provisioning root.
