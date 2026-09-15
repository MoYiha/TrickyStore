# Logging and Diagnostics

**Dil:** [English](../../../LOG.md) | **Türkçe** | [简体中文](../zh-CN/LOG.md) | [Español](../es/LOG.md) | [Deutsch](../de/LOG.md) | [Русский](../ru/LOG.md) | [Bahasa Indonesia](../id/LOG.md) | [हिन्दी](../hi/LOG.md) | [العربية](../ar/LOG.md)

CleveresTricky ayrı plain log file tutmaz; tanılama Android logcat'e yazılır. Temel komut `adb logcat -s cleverestricky CleveresTricky` şeklindedir. `Welcome to Service!`, web server/bridge başlangıç mesajları, Binder interceptor ve TEE kayıt mesajları faydalı startup marker'lardır.

`TAMPER DETECTED`, Binder ABI validation failure, rejected keybox veya injector timeout action gerektirir. Log yayımlamadan önce inceleyin; credential/token bilerek yazılmasa da file name, package, device property ve PID hassas olabilir.
