# Application Scope

**Sprache:** [English](../../../identity/ApplicationScope.md) | [Türkçe](../../tr/identity/ApplicationScope.md) | [简体中文](../../zh-CN/identity/ApplicationScope.md) | [Español](../../es/identity/ApplicationScope.md) | **Deutsch** | [Русский](../../ru/identity/ApplicationScope.md) | [Bahasa Indonesia](../../id/identity/ApplicationScope.md) | [हिन्दी](../../hi/identity/ApplicationScope.md) | [العربية](../../ar/identity/ApplicationScope.md)

Bestimmt, welche Android-App-UIDs Zertifikat-, Keybox- oder Identitätskompatibilität erhalten. Das Modul bietet zwei getrennte Zieldateien und zwei globale Modi:

- **Keybox-Ziele (`target.txt`)**: Definiert Pakete, die bei deaktiviertem globalen Keybox-Modus ein benutzerdefiniertes Keybox und TEE-Attestation-Zertifikat erhalten.
- **Identitäts-Ziele (`identity_target.txt`)**: Definiert Pakete für App-basierte Identitätseigenschaften (Build, Telephony, Region) bei deaktiviertem globalen Identitätsmodus.
- **Globaler Keybox-Modus**: Wendet benutzerdefinierte Keyboxen auf alle Benutzeranwendungen an, ohne `target.txt` zu erfordern. System- und Infrastruktur-UIDs bleiben geschützt.
- **Globaler Identitäts-Modus**: Wendet Build-Eigenschaften systemweit auf das gesamte Gerät an. Deaktiviert betrifft Identität nur `identity_target.txt` und zugewiesene Profile.
- **Unabhängiges Sicherheitspatch-Modul**: Das Sicherheitspatch kann unabhängig von der Identitäts-Engine in der Übersicht gesteuert werden.

Shared UIDs teilen die Binder-Identität. Ungültige Aktualisierungen schlagen geschlossen fehl und behalten den letzten gültigen Zustand bei.
