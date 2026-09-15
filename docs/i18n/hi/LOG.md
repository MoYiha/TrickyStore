# Logging and Diagnostics

**भाषा:** [English](../../../LOG.md) | [Türkçe](../tr/LOG.md) | [简体中文](../zh-CN/LOG.md) | [Español](../es/LOG.md) | [Deutsch](../de/LOG.md) | [Русский](../ru/LOG.md) | [Bahasa Indonesia](../id/LOG.md) | **हिन्दी** | [العربية](../ar/LOG.md)

Diagnostics Android logcat में जाते हैं, separate plaintext log नहीं। Command: `adb logcat -s cleverestricky CleveresTricky`। Service/bridge/Binder/TEE startup markers उपयोगी हैं।

`TAMPER DETECTED`, Binder ABI failure, rejected keybox, injector timeout जांचें। Publish से पहले filename/package/property/PID sensitivity review करें।
