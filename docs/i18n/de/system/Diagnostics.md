# Diagnostics

**Sprache:** [English](../../../system/Diagnostics.md) | [Türkçe](../../tr/system/Diagnostics.md) | [简体中文](../../zh-CN/system/Diagnostics.md) | [Español](../../es/system/Diagnostics.md) | **Deutsch** | [Русский](../../ru/system/Diagnostics.md) | [Bahasa Indonesia](../../id/system/Diagnostics.md) | [हिन्दी](../../hi/system/Diagnostics.md) | [العربية](../../ar/system/Diagnostics.md)

Zuerst Dashboard für Version, Engine, Profile, Keybox Count, Target Size, RKP, DRM und native Features prüfen und den ersten Fehler in Logs suchen. Wenn WebUI nicht startet: logcat, daemon, `webroot`, architecture-specific `webui_bridge` und Module-Manager-Status prüfen.

Copy Diagnostics unter Info & Resources kopiert eine begrenzte Support-Zusammenfassung mit englischen Schlüsseln und fester Allowlist. Enthalten sind Version, Root Environment, Native/Interceptor State, aggregierte Keybox/Rule Counts, Process CPU/RSS und Feature Flags; ausgeschlossen sind Logs, Package-/Keybox-Namen, Identity Values, Credentials, Server Configuration und Key Material. Vor dem Teilen prüfen, da Feature Flags die Modulkonfiguration beschreiben.

Zur Isolation Minimal + Reboot, genuine Verhalten bestätigen und Funktionen Schritt für Schritt aktivieren. Effective State zeigt Rule/Profile, Scope, Template, Keybox Ref, Privacy, Features, Patches, RKP/DRM, KeyMint/StrongBox, Provider Coexistence und Reboot Requirement ohne private Keys.
