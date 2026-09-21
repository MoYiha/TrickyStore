# Installer

**Sprache:** [English](../../../system/Installer.md) | [Türkçe](../../tr/system/Installer.md) | [简体中文](../../zh-CN/system/Installer.md) | [Español](../../es/system/Installer.md) | **Deutsch** | [Русский](../../ru/system/Installer.md) | [Bahasa Indonesia](../../id/system/Installer.md) | [हिन्दी](../../hi/system/Installer.md) | [العربية](../../ar/system/Installer.md)

Installiert den vollständigen KernelSU/APatch/Magisk-Payload für Android 12-17 auf ARM64/x86 64. Alle drei bieten volle WebUI-Unterstützung; unter Magisk öffnet sich die WebUI über die Action-Schaltfläche mithilfe der eigenständigen WebUI-Host-App (siehe [Magisk-Unterstützung](Magisk.md)). Recovery-Installationen werden abgewiesen.

Jeder Payload hat SHA 256; Runtime lehnt Symlinks, nicht reguläre und unerwartete Dateien ab. Interne Hashes beweisen nicht den Ersteller, daher veröffentlichen offizielle Releases zusätzlich `SHA256SUMS` und GitHub signed build provenance.
