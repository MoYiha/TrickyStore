# Logging and Diagnostics

**Язык:** [English](../../../LOG.md) | [Türkçe](../tr/LOG.md) | [简体中文](../zh-CN/LOG.md) | [Español](../es/LOG.md) | [Deutsch](../de/LOG.md) | **Русский** | [Bahasa Indonesia](../id/LOG.md) | [हिन्दी](../hi/LOG.md) | [العربية](../ar/LOG.md)

Diagnostics пишутся в Android logcat, отдельного plaintext log нет. Команда: `adb logcat -s cleverestricky CleveresTricky`. Startup markers service/bridge/Binder/TEE помогают диагностике.

`TAMPER DETECTED`, Binder ABI failure, rejected keybox, injector timeout требуют анализа. Перед публикацией проверять file names, packages, properties и PIDs.
