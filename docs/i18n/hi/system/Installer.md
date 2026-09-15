# Installer

**भाषा:** [English](../../../system/Installer.md) | [Türkçe](../../tr/system/Installer.md) | [简体中文](../../zh-CN/system/Installer.md) | [Español](../../es/system/Installer.md) | [Deutsch](../../de/system/Installer.md) | [Русский](../../ru/system/Installer.md) | [Bahasa Indonesia](../../id/system/Installer.md) | **हिन्दी** | [العربية](../../ar/system/Installer.md)

Android 12-17 ARM64/x86-64 पर full KernelSU/APatch module install करता है। Magisk/recovery partial install से पहले stop होते हैं।

हर payload SHA 256 से verified है; runtime symlink/non-regular/unexpected files reject करता है। Internal hash publisher proof नहीं है, इसलिए official Release में `SHA256SUMS` और GitHub signed build provenance है।
