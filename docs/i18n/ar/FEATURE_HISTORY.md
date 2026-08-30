# سجل الميزات والأعمال السابقة

يوثق هذا الملف تاريخ بعض الميزات الرئيسية في CleveresTricky وروابطها العامة، بهدف توفير سجل تاريخي واضح للإسناد. لا يُقصد به وحده الادعاء بنسخ الشيفرة المصدرية من مشروع آخر.

## هوية الجهاز وAttestation

- **#79 — إعدادات خاصة بالتطبيق ومعالجة `ATTESTATION_ID_*` (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
- **#139 — هوية جهاز عشوائية (2026-02-05)**
  https://github.com/tryigit/CleveresTricky/pull/139
- **#871 — التحكم في هوية الجهاز وDual-SIM على مستوى التطبيق (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/871
  يشمل IMEI وIMEI2 وMEID وIMSI وICCID ورقم الهاتف وSerial، مع نطاق التطبيق/الملف وآليات دورة الحياة.

## Keybox وAttestation

- **#77 — إدارة وتدوير عدة Keybox (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/77
- **#79 — التحقق من Keybox وأعمال هوية Attestation (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79

## بنية Native / Rust

- **#876 — بنية interceptor وRuntime lifecycle باستخدام Rust/Native (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/876

## ميزات أخرى في الوحدة

تشمل التطورات اللاحقة إدارة profile/template، تحديد النطاق حسب التطبيق، التحكم في دورة حياة hooks، عزل/حجب الهوية، تكاملات RKP/DRM، إدارة WebUI، وتكامل StrongBox/Attestation.

- #376 — https://github.com/tryigit/CleveresTricky/pull/376
- #476 — https://github.com/tryigit/CleveresTricky/pull/476
- #618 — https://github.com/tryigit/CleveresTricky/pull/618
- #908 — https://github.com/tryigit/CleveresTricky/pull/908
- #909 — https://github.com/tryigit/CleveresTricky/pull/909
- #910 — https://github.com/tryigit/CleveresTricky/pull/910
- #952 — https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — توجيه StrongBox إلى TEE وتوحيد مستوى أمان Attestation**
  https://github.com/tryigit/CleveresTricky/pull/1132
  تم التراجع عن هذا التغيير لاحقًا، ولذلك لا يتضمنه `master` الحالي.

## ملاحظة تاريخية

الروابط أعلاه هي سجلات GitHub العامة لتاريخ تطوير المشروع. تشابه الوظائف بين المشاريع لا يثبت بحد ذاته نسخ الشيفرة أو خرق الترخيص.
