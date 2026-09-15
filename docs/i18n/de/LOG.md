# Logging and Diagnostics

**Sprache:** [English](../../../LOG.md) | [Türkçe](../tr/LOG.md) | [简体中文](../zh-CN/LOG.md) | [Español](../es/LOG.md) | **Deutsch** | [Русский](../ru/LOG.md) | [Bahasa Indonesia](../id/LOG.md) | [हिन्दी](../hi/LOG.md) | [العربية](../ar/LOG.md)

Diagnosen gehen in Android logcat, nicht in eine separate Plaintext-Logdatei. Hauptkommando: `adb logcat -s cleverestricky CleveresTricky`. Service-, Bridge-, Binder- und TEE-Startup-Marker sind hilfreich.

`TAMPER DETECTED`, Binder-ABI-Fehler, abgewiesene Keybox oder Injector Timeout erfordern Analyse. Logs vor Veröffentlichung auf sensible Dateinamen, Packages, Properties und PIDs prüfen.
