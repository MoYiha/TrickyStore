# دليل Strong Integrity

**اللغة:** [English](../../../security/StrongIntegrityGuide.md) | [Türkçe](../../tr/security/StrongIntegrityGuide.md) | [简体中文](../../zh-CN/security/StrongIntegrityGuide.md) | [Español](../../es/security/StrongIntegrityGuide.md) | [Deutsch](../../de/security/StrongIntegrityGuide.md) | [Русский](../../ru/security/StrongIntegrityGuide.md) | [Bahasa Indonesia](../../id/security/StrongIntegrityGuide.md) | [हिन्दी](../../hi/security/StrongIntegrityGuide.md) | **العربية**

دليل سريع لاجتياز Google Play Integrity (`MEETS_STRONG_INTEGRITY`) باستخدام CleveresTricky حسب نوع الروم:

- **الروم الرسمي (Stock ROM)**: قم بتثبيت CleveresTricky وأضف Keybox صالحًا. هذا كل ما تحتاجه عادةً دون تغيير باقي الإعدادات.
- **روم رسمي مع تصحيح أمان قديم جدًّا**: من لوحة تحكم WebUI فعّل `Security Patch` واضبطه على `Auto`.
- **رومات AOSP**: من لوحة تحكم WebUI فعّل `Identity` وقم بانتحال بصمة الجهاز (يمكنك استخدام Auto Pixel Identity أو قالب جهاز معتمد). *(ملاحظة: قد يؤدي تفعيل Identity والانتحال التلقائي إلى زيادة طفيفة في استهلاك الذاكرة RAM بسبب المعالجة الديناميكية).*
- **الرومات المعدلة (Custom ROM)**: الرومات المعدلة غير مدعومة رسميًا. إذا كان Keystore معطلًا أو كان التحقق العتادي لا يعمل، فراجع الطريقة القديمة كبديل.

**أدوات واستيراد Keybox:**
- فاحص Keybox عبر الإنترنت: https://keybox.tryigit.dev/checker
- تحميل ومعلومات Keybox: https://keybox.tryigit.dev/
- طريقة الاستيراد: افتح CleveresTricky WebUI ← Keybox Manager وقم برفع الملف. لا حاجة للنسخ اليدوي إلى مجلدات TrickyStore القديمة.

**إخلاء المسؤولية وإشعار المجتمع:** CleveresTricky مشروع مفتوح المصدر مستقل يدعمه مجتمع CleveresTricky، وليس تابعًا لشركة Google LLC. قد تتغير سياسات واختبارات Google Play Integrity في أي وقت دون إشعار مسبق؛ لذا لا يمكن تقديم أي ضمان دائم. استخدم فقط المفاتيح المصرح لك باختبارها.

</div>
