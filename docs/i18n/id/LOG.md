# Logging and Diagnostics

**Bahasa:** [English](../../../LOG.md) | [Türkçe](../tr/LOG.md) | [简体中文](../zh-CN/LOG.md) | [Español](../es/LOG.md) | [Deutsch](../de/LOG.md) | [Русский](../ru/LOG.md) | **Bahasa Indonesia** | [हिन्दी](../hi/LOG.md) | [العربية](../ar/LOG.md)

Diagnostics ditulis ke Android logcat, tidak ada separate plaintext log. Perintah utama `adb logcat -s cleverestricky CleveresTricky`. Marker service/bridge/Binder/TEE membantu startup diagnosis.

`TAMPER DETECTED`, Binder ABI failure, rejected keybox, injector timeout perlu diperiksa. Review log sebelum publikasi karena filename/package/property/PID bisa sensitif.
