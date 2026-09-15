# Application Rules

**اللغة:** [English](../../../identity/ApplicationRules.md) | [Türkçe](../../tr/identity/ApplicationRules.md) | [简体中文](../../zh-CN/identity/ApplicationRules.md) | [Español](../../es/identity/ApplicationRules.md) | [Deutsch](../../de/identity/ApplicationRules.md) | [Русский](../../ru/identity/ApplicationRules.md) | [Bahasa Indonesia](../../id/identity/ApplicationRules.md) | [हिन्दी](../../hi/identity/ApplicationRules.md) | **العربية**

تسمح بتعيين template أو keybox محلي متحقق أو privacy policy لتطبيق مؤهل. القاعدة الصحيحة تعد target صريحا. `inherit` يحافظ على السياسة العامة، و`isolate` يشتق IMEI/IMSI/ICCID/MEID/phone/serial وattestation identifiers وDRM `deviceUniqueId` pseudonym مستقرة خاصة بالتطبيق، و`redact` يفرغ القيم المدعومة مع الحفاظ على أخطاء صلاحيات Android.

Attestation Identity يحتاج keybox فعالة ومتحققا منها. DRM isolation مستقل عن DRM Keystore Passthrough. Shared UID يحل بشكل حتمي عبر Package Manager ولا يتم الوثوق باسم package داخل الطلب. تنشر الحالة الجديدة atomically ويتم مسح الكاش المرتبط.
