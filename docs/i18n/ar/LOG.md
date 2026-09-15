# Logging and Diagnostics

**اللغة:** [English](../../../LOG.md) | [Türkçe](../tr/LOG.md) | [简体中文](../zh-CN/LOG.md) | [Español](../es/LOG.md) | [Deutsch](../de/LOG.md) | [Русский](../ru/LOG.md) | [Bahasa Indonesia](../id/LOG.md) | [हिन्दी](../hi/LOG.md) | **العربية**

Diagnostics تكتب إلى Android logcat ولا يوجد plaintext log مستقل. الأمر: `adb logcat -s cleverestricky CleveresTricky`. علامات service/bridge/Binder/TEE مفيدة لبدء التشغيل.

`TAMPER DETECTED` وBinder ABI failure وrejected keybox وinjector timeout تحتاج فحصا. راجع filenames/packages/properties/PIDs الحساسة قبل نشر log.
