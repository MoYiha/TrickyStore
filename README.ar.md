<div dir="rtl">

# CleveresTricky

**اللغة:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | **العربية**

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)
![Architecture](https://img.shields.io/badge/Arch-ARM64%20%7C%20x86--64-0969DA)

CleveresTricky هو موديول لـ KernelSU وAPatch مخصص لـ Android Keystore وattestation والهوية وتوافق التطبيقات. يجمع بين runtime أصلي مضبوط وWebUI موجه للهاتف، بحيث يمكن إدارة نطاق التطبيقات ومواد المفاتيح والهوية ومستويات التحديث وحماية Remote Key Provisioning وتوافق DRM من مكان واحد.

> هذه نسخة مترجمة من توثيق المستخدم. عند وجود اختلاف تقني تكون الوثائق الإنجليزية هي المرجع الأساسي.

## القدرات الرئيسية

### التحكم في runtime

[Spoof Engine](docs/i18n/ar.md#spoof-engine) يتحكم في استبدال الهوية الاختياري. تبقى حماية Keystore وTEE وboot properties الأساسية فعالة بشكل مستقل ما دامت خدمة الموديول سليمة.

[Application Scope](docs/i18n/ar.md#application-scope) يشرح Targeted Mode وGlobal Mode وقواعد الحزم وAndroid UID المشترك وتحديثات الكاش الحية.

[Application Rules](docs/i18n/ar.md#application-rules) يشرح القوالب الخاصة بالتطبيق واختيار keybox والهويات الخصوصية المستقرة.

[Profiles](docs/i18n/ar.md#profiles) يشرح Daily Compatibility وDefault وMaximum Compatibility وMinimal.

### Attestation والهوية

[Attestation](docs/i18n/ar.md#attestation) يشرح استبدال سلسلة الشهادات وعمليات KeyMint الحقيقية وStrongBox وحدود التوافق البرمجي.

[Certificate Safe Mode](docs/i18n/ar.md#certificate-safe-mode) يوثق مفهوم الإعداد القديم. الاستهداف الأساسي الحالي لم يعد يعتمد على ذلك المفتاح القديم.

[Keybox Manager](docs/i18n/ar.md#keybox-manager) يغطي تحميل keybox والتحقق والاختيار والتدوير وفحص الإلغاء والمراقبة.

[Automatic Keybox Check](docs/i18n/ar.md#automatic-keybox-check) يشرح عامل الصيانة المحدود ودورة حياته.

[Remote Sources](docs/i18n/ar.md#remote-sources) يشرح الجلب الموثق والتحقق من التوقيع وسياسة التحديث وسلوك الفشل.

[Encrypted Storage](docs/i18n/ar.md#encrypted-storage) يشرح حاويات CBOX والكاش المحلي المحمي والتعامل الآمن مع مواد المفاتيح.

[Patch Levels](docs/i18n/ar.md#patch-levels) يشرح حقول تحديث System وVendor وBoot والقواعد العامة والخاصة بالتطبيق.

[Build Identity](docs/i18n/ar.md#build-identity) يشرح قوالب الجهاز وfingerprint وحقول Build التي تراها التطبيقات والتفعيل المبكر المتزامن ومساعد Pixel beta Auto Identity لمستخدمي Custom ROM.

[Identity Refresh](docs/i18n/ar.md#identity-refresh) يشرح تجهيز الهوية للإقلاع التالي واتساق snapshot.

[Telephony Identity](docs/i18n/ar.md#telephony-identity) يشرح قيم شريحتي SIM والحفاظ على قرارات صلاحيات Android وواجهات API المدعومة وحدود مشغل الشبكة.

### توافق المنصة

[Boot Properties](docs/i18n/ar.md#boot-properties) يشرح عرض userspace الأساسي لخصائص الإقلاع وسياسة توافق الهوية المنفصلة.

[Region Properties](docs/i18n/ar.md#region-properties) يشرح عرض البلد ومنطقة العتاد الاختياري والمحدود.

[Provider Coexistence](docs/i18n/ar.md#provider-coexistence) يشرح كيف يمنع automatic mode الكتابة فوق مزود fingerprint آخر.

[RKP Protection](docs/i18n/ar.md#rkp-protection) يشرح بنية Android المحمية وgenerated-key passthrough الحقيقي.

[DRM Passthrough and Privacy](docs/i18n/ar.md#drm-passthrough) يفصل بين وظيفتين. يمكن لتطبيقات الوسائط المختارة البقاء على مسار شهادات Keystore الحقيقي في Android، بينما يمكن لـ `privacy=isolate` استبدال `deviceUniqueId` المدعوم في DRM الحديث باسم مستعار ثابت خاص بالتطبيق.

هذه ليست أداة لتجاوز Widevine أو DRM. لا يتم تغيير مستوى الأمان أو التراخيص أو provisioning أو مفاتيح المحتوى أو الجلسات أو HDCP أو خصائص النص.

### الواجهة والتشغيل

[Web Interface](docs/i18n/ar.md#web-interface) يشرح نقل أوامر مدير الموديول الأصلي والتنقل على الهاتف والحالة الحية والتحقق وإمكانية الوصول.

اللغات المضمنة في WebUI هي **English** و**Türkçe** و**简体中文** و**Español** و**Deutsch** و**Русский** و**Bahasa Indonesia** و**हिन्दी** و**العربية**. كتالوجات الترجمة محلية ولا يحتاج تبديل اللغة إلى اتصال بالشبكة.

[Backup and Restore](docs/i18n/ar.md#backup-restore) يشرح التصدير المشفر والاستيراد المحدود والاستعادة الآمنة.

[Installer](docs/i18n/ar.md#installer) يشرح بنية حزمة KernelSU/APatch والتحقق من payload والأجهزة المدعومة ومسار التثبيت.

[Diagnostics](docs/i18n/ar.md#diagnostics) يشرح السجلات وفحوص الحالة والأخطاء الشائعة وتسلسل استكشاف المشاكل المنضبط.

### مراجع هندسية

[Security Model](docs/i18n/ar.md#security-model) يوثق حدود الثقة والملفات المحمية والتحقق من الإدخال والقدرات التي لا يدعيها الموديول.

[Performance](docs/i18n/ar.md#performance) يوثق دورة حياة hook والكاش المحدود والعمل الخلفي واستخدام CPU والذاكرة.

[Building](docs/i18n/ar.md#building) يوثق toolchain ومهام التحقق والملفات الناتجة.

[Native Architecture](docs/i18n/ar.md#native-architecture) يوثق Rust injector وRust native core وسياسة اللغات وحد Android C++ ABI الوحيد المطلوب.

## البدء السريع

1. نزّل ZIP الحالي من صفحة Releases الرسمية للمشروع.
2. عند الحاجة للتحقق من مصدر البناء الرسمي افحص `SHA256SUMS` وGitHub build provenance.
3. افتح KernelSU أو APatch أثناء تشغيل Android.
4. ثبّت ZIP ثم أعد التشغيل.
5. افتح CleveresTricky WebUI من مدير الموديول.
6. يبدأ التثبيت الجديد مع Global Mode مفعلا وidentity spoofing الاختياري متوقفا.
7. أضف فقط مواد مفاتيح تملكها أو لديك تصريح باختبارها.
8. اضبط خيارات الهوية فقط عند الحاجة.
9. أعد التشغيل بعد تغيير قيم Template Build Identity.

لا يحتوي المشروع أو حزمة release على keybox قابل للاستخدام أو مفتاح attestation خاص.

## البيئة المدعومة

يدعم CleveresTricky Android 12 حتى Android 17، أي API 31 حتى 37، على ARM64 وx86 64. يتم التثبيت من KernelSU أو APatch أثناء تشغيل Android.

Magisk وrecovery غير مدعومين. يوقف Installer المسار غير المدعوم قبل ترك موديول ناقص.

## حدود مهمة

تعتمد النتائج على حالة الجهاز الحقيقية وfirmware والشهادات ومواد المفاتيح وGoogle Play services وسياسة الخدمة البعيدة. يحسن CleveresTricky مسار التوافق المحلي لكنه لا يضمن verdict بعيدا محددا.

قيم Telephony تظهر فقط عبر APIs المدعومة للتطبيقات ولا تغير modem أوbaseband أوEFS أوSIM الفعلية أو الهوية التي يراها مشغل الشبكة.

في Android الحديث يكون Android ID مقيدا بهوية توقيع التطبيق والمستخدم والجهاز داخل SettingsProvider. لا يقدم CleveresTricky تحكما عالميا مضللا في Android ID.

إصدار kernel الحقيقي لا يتغير. عرض boot properties لا يعيد قفل bootloader فعليا ولا يصلح verified boot ولا يعيد كتابة vbmeta ولا يغير hardware root of trust.

فتح bootloader لا يعني تلقائيا أن كل DRM أصبح غير قابل للاستخدام. السلوك يعتمد على الجهاز وتنفيذ vendor وحالة provisioning ومستوى الأمان وfirmware وسياسة الخدمة. عمل DRM الحالي يركز على الخصوصية وليس bypass.

تكتشف سجلات SHA 256 الداخلية payload المفقود أو المعدل أو المضاف أو المرتبط أو غير المتوقع، وتدخل الخدمة في tamper lockdown عند فشل التحقق. أصالة release الرسمي تعتمد على digest منشور بشكل منفصل وGitHub signed build provenance.

## الإعداد الأول الموصى به

ابدأ بإعدادات التثبيت الجديد. يحدد Global Mode تطبيقات UID المناسبة بينما تبقى حماية boot وKeystore الأساسية فعالة. يبقى identity spoofing الاختياري متوقفا حتى تفعله من قسم Identity.

لمستخدمي Custom ROM يمكن لـ Auto Identity جلب Pixel beta أو canary Build Identity من بيانات Google العامة وحفظها محليا. فعّل Identity Spoof Engine وأعد التشغيل فقط عندما تريد إظهار هذه القيم فعليا.

لخصوصية DRM identifier أنشئ Application Rule لتطبيق الوسائط واضبط `privacy=isolate`. يمكن أن يبقى DRM Keystore Passthrough مفعلا لأن مسار شهادة Keystore الحقيقية ومسار DRM ID المستعار مستقلان.

## المساعدة ومعلومات المشروع

للتشخيص استخدم WebUI Logs أو Android logcat مع الوسم `CleveresTricky`. التفاصيل في [Diagnostics](docs/i18n/ar.md#diagnostics).

تاريخ المشروع في [CHANGELOG](docs/i18n/ar.md#changelog)، والمساهمة في [Contributing](docs/i18n/ar.md#contributing)، واللغات في [Language Support](docs/i18n/ar.md#languages)، والثيم في [Theme](docs/i18n/ar.md#theme).

تبقى [وثائق Android attestation الرسمية](https://source.android.com/docs/security/features/keystore/attestation) و[وثائق Play Integrity verdict](https://developer.android.com/google/play/integrity/verdicts) و[دليل موديولات KernelSU](https://kernelsu.org/guide/module.html) هي المراجع المعتمدة لمنصاتها.

## تحكم اختياري دقيق بالهوية

يعالج CleveresTricky بشكل مستقل Device and Build Identity وAttestation Identity وTelephony Identity وRegion Identity وIdentity Refresh وSecurity Patch. Security Patch مستقل عن Device and Build Identity.

تبقى Keystore interception الأساسية وعمليات private key الحقيقية في KeyMint وStrongBox ومعالجة root of trust وأمان Binder وتوافق boot الضروري مستقلة عن هذه الخيارات. تفصل الواجهة بين الحالة الحقيقية الملتقطة والحالة المعدة والحالة الفعلية التي يراها التطبيق.

يوفر Security Patch سياسات مستقلة لـ System وVendor وBoot. يمكن لـ Profiles تعيين إعدادات متناسقة للتطبيقات، ويعرض Effective State inspector النتيجة النهائية للـ resolver.

</div>