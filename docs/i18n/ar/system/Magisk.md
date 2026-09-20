# دعم Magisk

**اللغة:** [English](../../../system/Magisk.md) | [Türkçe](../../tr/system/Magisk.md) | [简体中文](../../zh-CN/system/Magisk.md) | [Español](../../es/system/Magisk.md) | [Deutsch](../../de/system/Magisk.md) | [Русский](../../ru/system/Magisk.md) | [Bahasa Indonesia](../../id/system/Magisk.md) | [हिन्दी](../../hi/system/Magisk.md) | **العربية**

## بيان الدعم

يدعم CleveresTricky رسمياً بيئات الروت الثلاث، مع واجهة WebUI عادية في كل منها:

* **[KernelSU](https://kernelsu.org)** - تُفتح WebUI من زر الوحدة في تطبيق المدير.
* **[APatch](https://apatch.dev)** - تُفتح WebUI من زر الوحدة في تطبيق المدير.
* **Magisk** - تُفتح WebUI من زر **Action** الخاص بالوحدة عبر تطبيق مضيف WebUI (انظر أدناه).

> [!NOTE]
> تفحص أطر الكشف الحديثة وGoogle Play Integrity نقاط التثبيت في مساحة المستخدم وملفات الروت الثنائية، لذا قد توفر حلول مستوى النواة إخفاءً أقوى على المدى الطويل. مقارنة معمارية:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## WebUI على Magisk

لا ينفذ Magisk واجهة WebUI الخاصة بالوحدات بنفسه، لذا يفتح زر Action في Magisk واجهة CleveresTricky داخل **[تطبيق مضيف WebUI مستقل](https://github.com/adivenxnataly/KsuWebUI)**:

1. في تطبيق Magisk، افتح وحدة CleveresTricky واضغط **Action**.
2. عند الاستخدام الأول، يقوم المشغّل بتنزيل APK مضيف WebUI من إصدارات GitHub الخاصة به وتثبيته. يتم التنزيل مرة واحدة فقط.
3. يفتح المشغّل بعد ذلك المضيف بمعرف الوحدة `cleverestricky`، الذي يُحل إلى `/data/adb/modules/cleverestricky/webroot` (`index.html` مع ملفات `bridge.js` و`policy.js` و`ux.js` الحالية).

المصطلحات، لأن الأسماء مربكة:

* **تطبيق مضيف WebUI** هو تطبيق مضيف/حاوية WebUI مستقل. وهو **ليس** KernelSU و**لا** يثبت أي مدير روت؛ يبقى Magisk حل الروت الوحيد على الجهاز.
* **CleveresTricky WebUI** هي واجهة الوحدة الخاصة في `/data/adb/modules/cleverestricky/webroot`.
* **خلفية الروت** هي بيئة تشغيل CleveresTricky الأصلية/Rust (`cleverestrickyd` والخلفية و`webui_bridge`)، دون تغيير في البيئات الثلاث. تطبيق المضيف مجرد حاوية WebView بصلاحية shell الجذر؛ مسار الاتصال الحالي من `bridge.js` إلى `webui_bridge` لم يُمس.

---

## التهيئة اليدوية

تفضل تحرير الملفات يدوياً أو تعذّر الوصول إلى WebUI؟ راجع دليل [التهيئة اليدوية](ManualConfiguration.md) المستقل. أرشيف التقرير الجاهز:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
سيتم حفظ الأرشيف التشخيصي داخل الدليل `/data/adb/cleverestricky/bugreports/`.
