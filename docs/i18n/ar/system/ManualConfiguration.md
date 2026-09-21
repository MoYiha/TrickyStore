# التهيئة اليدوية

**اللغة:** [English](../../../system/ManualConfiguration.md) | [Türkçe](../../tr/system/ManualConfiguration.md) | [简体中文](../../zh-CN/system/ManualConfiguration.md) | [Español](../../es/system/ManualConfiguration.md) | [Deutsch](../../de/system/ManualConfiguration.md) | [Русский](../../ru/system/ManualConfiguration.md) | [Bahasa Indonesia](../../id/system/ManualConfiguration.md) | [हिन्दी](../../hi/system/ManualConfiguration.md) | **العربية**

يغطي هذا الدليل التهيئة اليدوية لـ CleveresTricky عبر الملفات دون استخدام WebUI. تبقى WebUI هي الطريقة العادية لتغيير الإعدادات؛ استخدم هذا الدليل إذا كنت تفضل تحرير الملفات يدوياً أو إذا تعذّر الوصول إلى WebUI. توجد جميع ملفات الإعدادات والسياسات داخل الدليل `/data/adb/cleverestricky/`.

---

## 1. النطاق والتطبيقات المستهدفة

* **`target.txt`**: قائمة بأسماء الحزم المستهدفة لتزييف مصادقة Keystore (حزمة واحدة في كل سطر):
  ```text
  com.google.android.gms
  com.google.android.gms.unstable
  com.android.vending
  ```
* **`global_mode`**: ملف علامة فارغ.
  * **عند وجوده**: يكون الوضع العام نشطاً (يتم اعتراض جميع التطبيقات غير التابعة للنظام أو الروت).
  * **عند غيابه**: يتم اعتراض التطبيقات المحددة فقط في `target.txt`.
  * *الأمر:* `touch /data/adb/cleverestricky/global_mode` (تفعيل) أو `rm -f /data/adb/cleverestricky/global_mode` (تعطيل).

* **`identity_target.txt`**: التطبيقات المستهدفة لتزييف هوية وبنية الجهاز.
* **`global_identity_mode`**: ملف علامة لتطبيق تزييف الهوية بشكل عام.

---

## 2. هوية الجهاز وخصائص البناء

* **`spoof_build_vars`**: خصائص الجهاز المطلوب تزييفها بصيغة `KEY=VALUE`:
  ```properties
  MANUFACTURER=Google
  MODEL=Pixel 8 Pro
  FINGERPRINT=google/husky/husky:14/UQ1A.240105.004/11269998:user/release-keys
  BRAND=google
  PRODUCT=husky
  DEVICE=husky
  RELEASE=14
  ID=UQ1A.240105.004
  INCREMENTAL=11269998
  TYPE=user
  TAGS=release-keys
  ```
* **`security_patch.txt`**: تاريخ التحديث الأمني (مثال `2026-03-05`). اتركه فارغاً للمطابقة التلقائية مع النظام.
* **`boot_props_mode`**: التحكم في محاكاة خصائص البوتلودر (`auto` أو `force` أو `disable`).

---

## 3. مفاتيح العتاد (Keybox)

* **`keybox.xml`**: ضع ملف XML لمفتاح المصادقة العتادي مباشرة في المسار `/data/adb/cleverestricky/keybox.xml`. تأكد من تأمين الصلاحيات (`chmod 600`).
* **`keyboxes/`**: دليل لحفظ ملفات keybox متعددة.
* **الرفع لا يستبدل الملفات أبداً:** يُحفظ Keybox الذي تسحبه أو تلصقه بالاسم المُدخل، وباسم `keybox.xml` عندما لا يُحدَّد اسم؛ وإذا كان الاسم مستخدماً بالفعل يُستخدم تلقائياً الاسم الحر التالي (`keybox2.xml`، `keybox3.xml`، ...).
* **`disabled_keyboxes`**: قائمة الاستبعاد من المجموعة. كل سطر يحتوي على معرّف `النطاق:اسم الملف` (`keyboxes:keybox2.xml`، `root:keybox.xml`) يطابق الأسماء المعروضة في لوحة Keybox في WebUI. تبقى مفاتيح Keybox المدرجة ظاهرة وقابلة للإدارة لكنها لا تُحمَّل أبداً في مجموعة التصديق. تقرأ أزرار التعطيل والتفعيل في WebUI هذا الملف وتكتبه، لذا يمكن إدارته يدوياً أيضاً.

---

## 4. نطاق حماية DRM والخصوصية

* **`drm_packages.txt`**: تطبيقات الوسائط المعفاة من اعتراض Keystore للحفاظ على دعم Widevine L1:
  ```text
  com.netflix.mediaclient
  com.amazon.avod.thirdpartyclient
  com.disney.disneyplus
  ```

---

## 5. ملفات العلامات للخصائص (Feature Flags)

لتفعيل خاصية قم بإنشاء ملفها (`touch <file>`) ولتعطيلها احذف الملف (`rm -f <file>`):

| ملف العلامة | التأثير عند الوجود |
| :--- | :--- |
| `spoof_enabled` | تفعيل محرك تزييف الهوية. |
| `spoof_build_identity` | تفعيل تزييف خصائص البناء. |
| `auto_keybox_check` | التحقق التلقائي الدوري من صلاحية مفاتيح keybox وإلغائها. |
| `drm_passthrough` | تفعيل استثناء DRM للحزم المسجلة في `drm_packages.txt`. |
| `hide_sensitive_props` | إخفاء مؤشرات الروت والتصحيح وحالة البوتلودر الحساسة. |
| `tee_broken_mode` | حالة ترحيل/توافق قديمة. عند وجوده يحتفظ الخدمة بمعالجة الترحيل القديمة؛ تظل الحماية الجوهرية دون تغيير ويعمل قاطع الدائرة الفاشل بشكل منفصل عند فشل اتصال TEE العتادي. |
| `debug_logging` | تفعيل تسجيل السجلات البرمجية المفصلة في `native_runtime.log`. |

---

## تطبيق التغييرات والتحقق

### تطبيق الإعدادات
تُطبق التغييرات من WebUI عبر مسار التشغيل العادي. إذا حررت ملفات الإعدادات أو العلامات يدوياً، يُنصح بإعادة تشغيل الجهاز بعد ذلك:
```sh
su -c "reboot"
```

### فحص سجلات التشغيل
```sh
su -c "cat /data/adb/cleverestricky/native_runtime.log"
```

### التحقق من العمليات النشطة
```sh
su -c "ps -A | grep -E 'cleverestrickyd|cleverestricky_backend'"
```

### إنشاء تقرير تشخيصي شامل
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
(على Magisk، يفتح زر Action واجهة WebUI، لذا شغّل هذا الملف مباشرة).
سيتم حفظ الأرشيف التشخيصي داخل الدليل `/data/adb/cleverestricky/bugreports/`.
