# Application Scope

**اللغة:** [English](../../../identity/ApplicationScope.md) | [Türkçe](../../tr/identity/ApplicationScope.md) | [简体中文](../../zh-CN/identity/ApplicationScope.md) | [Español](../../es/identity/ApplicationScope.md) | [Deutsch](../../de/identity/ApplicationScope.md) | [Русский](../../ru/identity/ApplicationScope.md) | [Bahasa Indonesia](../../id/identity/ApplicationScope.md) | [हिन्दी](../../hi/identity/ApplicationScope.md) | **العربية**

يحدد أي تطبيقات Android تتلقى توافق الشهادات/Keybox أو الهوية (Identity). تتضمن الوحدة ملفي أهداف ووضعين عامين منفصلين:

- **أهداف Keybox (`target.txt`)**: تحدد الحزم التي تتلقى Keybox المخصص وتصديق TEE عند تعطيل الوضع العام لـ Keybox.
- **أهداف الهوية (`identity_target.txt`)**: تحدد الحزم لخصائص الهوية الخاصة بكل تطبيق (Build، Telephony، Region) عند تعطيل الوضع العام للهوية.
- **الوضع العام لـ Keybox**: يطبق Keybox المخصص على جميع تطبيقات المستخدم دون الحاجة إلى `target.txt`. تبقى معرفات النظام والبنية التحتية محمية.
- **الوضع العام للهوية**: يطبق خصائص Build على مستوى النظام بالكامل. عند تعطيله، تؤثر الهوية فقط على `identity_target.txt` والملفات الشخصية المعينة.
- **وحدة تصحيح الأمان المستقلة**: يمكن إدارة تصحيح الأمان بشكل مستقل عن محرك الهوية من لوحة التحكم.

تشترك الحزم ذات UID المشترك في هوية Binder. يتم رفض التحديثات غير الصالحة بشكل مغلق (fail-closed).
