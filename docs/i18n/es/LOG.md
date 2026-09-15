# Logging and Diagnostics

**Idioma:** [English](../../../LOG.md) | [Türkçe](../tr/LOG.md) | [简体中文](../zh-CN/LOG.md) | **Español** | [Deutsch](../de/LOG.md) | [Русский](../ru/LOG.md) | [Bahasa Indonesia](../id/LOG.md) | [हिन्दी](../hi/LOG.md) | [العربية](../ar/LOG.md)

CleveresTricky escribe diagnostics en Android logcat y no guarda un log plaintext separado. Comando principal: `adb logcat -s cleverestricky CleveresTricky`. Los markers de service, bridge, Binder interceptor y TEE ayudan a revisar startup.

`TAMPER DETECTED`, fallo Binder ABI, keybox rechazado o injector timeout requieren atención. Revisa logs antes de publicarlos porque nombres de archivo, packages, properties y PID pueden ser sensibles.
