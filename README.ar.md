# CleveresTricky

**اللغة:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | **العربية**

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
[![التنزيلات](https://img.shields.io/github/downloads/tryigit/CleveresTricky/total?color=0A84FF&label=%D8%A7%D9%84%D8%AA%D9%86%D8%B2%D9%8A%D9%84%D8%A7%D8%AA)](https://github.com/tryigit/CleveresTricky/releases)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)

CleveresTricky هو موديول لـ KernelSU وAPatch على Android 12-17. يجمع توافق Android Keystore وattestation، وإدارة Keybox/CBOX، وتحديد التطبيقات، وخيارات الهوية الاختيارية، ومستويات التصحيح، وأدوات الخصوصية داخل WebUI واحد مناسب للهاتف.

ابدأ بالإعدادات الافتراضية وفعّل فقط الميزات التي تحتاجها فعلاً.

## ماذا يمكنك أن تفعل؟

- إدارة ملفات **Keybox/CBOX** والتحقق منها واختيارها وتبديلها.
- استخدام Global Mode أو تطبيق قواعد منفصلة على تطبيقات محددة.
- ضبط عرض device/build وattestation والاتصالات والمنطقة وsecurity patch عند الحاجة.
- حماية تدفقات Remote Key Provisioning وتقليل كشف معرّفات DRM المدعومة بدون الادعاء بتجاوز DRM.
- نسخ الإعدادات احتياطياً، ومراجعة الحالة الفعلية، وجمع التشخيصات من WebUI أو Action الخاص بالموديول.

## البدء السريع

1. نزّل أحدث ملف ZIP من صفحة [Releases](https://github.com/tryigit/CleveresTricky/releases/latest) الرسمية.
2. ثبّت ملف ZIP من خلال KernelSU أو APatch أثناء تشغيل Android.
3. افتح WebUI الخاص بـ CleveresTricky من مدير الموديولات.
4. أضف فقط **Keybox أو CBOX** تملكه أو لديك تصريح لاختباره.
5. استخدم الإعدادات الافتراضية أولاً، ثم فعّل خيارات الهوية أو قواعد التطبيقات أو الخصوصية فقط عند الحاجة.

لا يتضمن المشروع Keybox جاهزاً للاستخدام ولا مفتاح attestation خاصاً.

## البيئة المدعومة

- Android **12-17** / API **31-37**
- **ARM64** و **x86-64**
- **KernelSU** و **APatch**

Magisk والتثبيت من recovery غير مدعومين.

## أمور مهمة

يحسّن CleveresTricky مسار التوافق المحلي، لكن النتيجة البعيدة تظل مرتبطة بالجهاز الحقيقي والـ firmware وحالة الاعتماد وGoogle Play services وسياسة الخادم والبيانات التي تضبطها. لا يمكنه ضمان نتيجة محددة لـ Play Integrity أو attestation.

لا يعيد قفل bootloader فعلياً، ولا يعيد كتابة قياسات Verified Boot، ولا يغيّر hardware Root of Trust، ولا يعدّل modem/baseband، ولا يحوّل أدوات خصوصية DRM إلى DRM bypass.

استخدم فقط الإعدادات وبيانات الاعتماد التي لديك صلاحية لاستخدامها.

## المزيد من المعلومات

- [Keybox Manager](docs/KeyboxManager.md) - تحميل Keybox/CBOX والتحقق منها واختيارها وفحص الإلغاء.
- [Application Scope](docs/ApplicationScope.md) و [Application Rules](docs/ApplicationRules.md) - تحديد التطبيقات التي تنطبق عليها الميزات.
- [Build Identity](docs/BuildIdentity.md) و [Telephony Identity](docs/TelephonyIdentity.md) و [Patch Levels](docs/PatchLevels.md) - خيارات الهوية الاختيارية.
- [RKP Protection](docs/RkpProtection.md) و [DRM Privacy](docs/DrmPassthrough.md) - توافق المنصة وسلوك الخصوصية.
- [Backup and Restore](docs/BackupRestore.md) - نسخ الإعدادات المشفّر واستعادته.
- [Security Model](docs/SecurityModel.md) و [Installer](docs/Installer.md) - حدود الثقة وتفاصيل التثبيت.

## تحتاج مساعدة؟

استخدم صفحة **Logs** في WebUI أو **Action** الخاص بالموديول لإنشاء تقرير تشخيص طارئ. راجع الأرشيف قبل مشاركته لأن التشخيصات قد تحتوي على معلومات عن الجهاز والنظام.

راجع [Diagnostics](docs/Diagnostics.md) للمشاكل الشائعة وخطوات استكشاف الأخطاء.

## المشروع

[سجل التغييرات](CHANGELOG.md) · [المساهمة](CONTRIBUTING.md) · [اللغات](LANGUAGES.md) · [الترخيص](LICENSING.md) · [Telegram](https://t.me/cleverestech)
