# CleveresTricky

**Sprache:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | **Deutsch** | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Build](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml/badge.svg)](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml)

CleveresTricky ist ein KernelSU- und APatch-Modul für Android Keystore, Attestation, Identität und App-Kompatibilität. Es verbindet eine kontrollierte native Laufzeit mit einer mobilen WebUI, sodass Anwendungsbereich, Schlüsselmaterial, Identität, Patch-Level, Remote-Key-Provisioning-Schutz und DRM-Kompatibilität zentral verwaltet werden können.

> Diese Datei ist eine lokalisierte Benutzerdokumentation. Bei technischen Abweichungen ist die englische Dokumentation die kanonische Referenz.

## Hauptfunktionen

### Laufzeitsteuerung

[Spoof Engine](docs/i18n/de.md#spoof-engine) steuert optionale Identitätsersetzung. Kernschutz für Keystore, TEE und Boot Properties bleibt unabhängig aktiv, solange der Moduldienst gesund ist.

[Application Scope](docs/i18n/de.md#application-scope) beschreibt Targeted Mode, Global Mode, Paketregeln, gemeinsam genutzte Android UIDs und Live-Cache-Updates.

[Application Rules](docs/i18n/de.md#application-rules) beschreibt app-spezifische Templates, Keybox-Auswahl und stabile Datenschutzidentitäten.

[Profiles](docs/i18n/de.md#profiles) beschreibt Daily Compatibility, Default, Maximum Compatibility und Minimal.

### Attestation und Identität

[Attestation](docs/i18n/de.md#attestation) beschreibt Zertifikatskettenersetzung, echte KeyMint-Operationen, StrongBox und die Grenzen softwarebasierter Kompatibilität.

[Certificate Safe Mode](docs/i18n/de.md#certificate-safe-mode) dokumentiert das ältere Konfigurationskonzept. Das aktuelle Kern-Targeting hängt nicht mehr davon ab.

[Keybox Manager](docs/i18n/de.md#keybox-manager) behandelt Laden, Prüfung, Auswahl, Rotation, Widerrufsprüfung und automatische Überwachung von Keyboxes.

[Automatic Keybox Check](docs/i18n/de.md#automatic-keybox-check) erklärt den begrenzten Wartungs-Worker und seinen Lebenszyklus.

[Remote Sources](docs/i18n/de.md#remote-sources) beschreibt authentifizierten Abruf, Signaturprüfung, Refresh-Politik und Fehlerverhalten.

[Encrypted Storage](docs/i18n/de.md#encrypted-storage) erklärt CBOX-Container, lokale geschützte Caches und sicheren Umgang mit Schlüsselmaterial.

[Patch Levels](docs/i18n/de.md#patch-levels) erklärt System-, Vendor- und Boot-Patchfelder mit globalen und app-spezifischen Regeln.

[Build Identity](docs/i18n/de.md#build-identity) erklärt Gerätetemplates, Fingerprint, app-sichtbare Build-Felder, synchronisierte Early-Boot-Aktivierung und den optionalen Pixel-beta-Auto-Identity-Helfer für Custom ROMs.

[Identity Refresh](docs/i18n/de.md#identity-refresh) erklärt die Identität für den nächsten Boot und Snapshot-Konsistenz.

[Telephony Identity](docs/i18n/de.md#telephony-identity) erklärt Dual-SIM-Werte, Erhalt von Berechtigungsentscheidungen, unterstützte Android-APIs und Netzbetreibergrenzen.

### Plattformkompatibilität

[Boot Properties](docs/i18n/de.md#boot-properties) erklärt die zentrale Userspace-Boot-Property-Ansicht und die getrennte Identitätskompatibilitätspolitik.

[Region Properties](docs/i18n/de.md#region-properties) erklärt die optionale begrenzte Länder- und Hardware-Regionsansicht.

[Provider Coexistence](docs/i18n/de.md#provider-coexistence) erklärt, wie Automatic Mode das Überschreiben eines anderen Fingerprint-Anbieters verhindert.

[RKP Protection](docs/i18n/de.md#rkp-protection) erklärt geschützte Android-Infrastruktur und echten Generated-Key-Passthrough.

[DRM Passthrough and Privacy](docs/i18n/de.md#drm-passthrough) trennt zwei Funktionen. Ausgewählte Medien-Apps können auf dem echten Android-Keystore-Zertifikatspfad bleiben, während `privacy=isolate` beim unterstützten modernen DRM-HAL-`deviceUniqueId` ein stabiles app-spezifisches Pseudonym liefern kann.

Dies ist kein Widevine- oder DRM-Bypass. Sicherheitslevel, Lizenzen, Provisioning, Content Keys, Sitzungen, HDCP und String Properties werden nicht verändert.

### Oberfläche und Betrieb

[Web Interface](docs/i18n/de.md#web-interface) erklärt den nativen Modulmanager-Transport, mobile Navigation, Live-Status, Validierung und Barrierefreiheit.

Integrierte WebUI-Sprachen sind **English**, **Türkçe**, **简体中文**, **Español**, **Deutsch**, **Русский**, **Bahasa Indonesia**, **हिन्दी** und **العربية**. Alle Kataloge liegen lokal im Modul, ein Sprachwechsel benötigt kein Netzwerk.

[Backup and Restore](docs/i18n/de.md#backup-restore) erklärt verschlüsselte Exporte, begrenzte Importe und sichere Wiederherstellung.

[Installer](docs/i18n/de.md#installer) erklärt KernelSU/APatch-Paketlayout, Payload-Prüfung, unterstützte Geräte und Installationsablauf.

[Diagnostics](docs/i18n/de.md#diagnostics) behandelt Logs, Statusprüfungen, häufige Fehler und eine kontrollierte Fehlersuchreihenfolge.

### Engineering-Referenzen

[Security Model](docs/i18n/de.md#security-model) dokumentiert Vertrauensgrenzen, geschützte Dateien, Eingabevalidierung und Fähigkeiten, die das Modul ausdrücklich nicht behauptet.

[Performance](docs/i18n/de.md#performance) dokumentiert Hook-Lebenszyklus, begrenzte Caches, Hintergrundarbeit, CPU und Speicher.

[Building](docs/i18n/de.md#building) dokumentiert Toolchain, Validierungen und erzeugte Artefakte.

[Native Architecture](docs/i18n/de.md#native-architecture) dokumentiert Rust-Injector, Rust Native Core, Sprachrichtlinie und die einzige notwendige Android-C++-ABI-Grenze.

## Schnellstart

1. Aktuelles Release-ZIP von der offiziellen Projekt-Release-Seite laden.
2. Bei Bedarf `SHA256SUMS` und GitHub Build Provenance prüfen.
3. KernelSU oder APatch bei laufendem Android öffnen.
4. ZIP installieren und neu starten.
5. CleveresTricky WebUI aus dem Modulmanager öffnen.
6. Neue Installationen starten mit Global Mode an und optionalem Identity Spoofing aus.
7. Nur eigenes oder autorisiert zu testendes Schlüsselmaterial hinzufügen.
8. Identitätsoptionen nur bei Bedarf konfigurieren.
9. Nach Änderungen an Template-Build-Identity-Werten neu starten.

Das Projekt enthält keine nutzbare Keybox und keinen privaten Attestation-Schlüssel.

## Unterstützte Umgebung

Unterstützt werden Android 12 bis Android 17, API 31 bis 37, ARM64 und x86 64. Die Installation erfolgt bei laufendem Android über KernelSU oder APatch.

Magisk- und Recovery-Installationen werden nicht unterstützt. Der Installer stoppt solche Pfade, bevor ein unvollständiges Modul zurückbleibt.

## Wichtige Grenzen

Ergebnisse hängen vom realen Gerätezustand, Firmware, Zertifizierung, Schlüsselmaterial, Google Play services und Remote-Policy ab. CleveresTricky verbessert lokale Kompatibilität, garantiert aber keinen bestimmten Remote-Verdict.

Telephony-Werte sind nur über unterstützte App-APIs sichtbar. Modem, Baseband, EFS, physische SIM und die beim Netzbetreiber sichtbare Identität bleiben unverändert.

Android ID ist auf modernem Android nach App-Signatur, Nutzer und Gerät in SettingsProvider begrenzt. CleveresTricky bietet keinen irreführenden globalen Android-ID-Schalter.

Die reale Kernel-Version bleibt unverändert. Boot Properties sperren den Bootloader nicht physisch, reparieren Verified Boot nicht, schreiben vbmeta nicht neu und ändern keinen Hardware Root of Trust.

Ein entsperrter Bootloader bedeutet nicht automatisch, dass jedes DRM unbrauchbar ist. Verhalten hängt von Gerät, Vendor-Implementierung, Provisioning, Sicherheitslevel, Service-Policy und Firmware ab. Die aktuelle DRM-Arbeit ist datenschutzorientiert und kein Bypass.

Interne SHA-256-Datensätze erkennen fehlende, geänderte, injizierte, verlinkte und unerwartete Payloads und setzen den Dienst bei Fehlern in Tamper Lockdown. Die Authentizität offizieller Releases wird durch separat veröffentlichten Digest und GitHub-signierte Build Provenance abgesichert.

## Empfohlene Ersteinrichtung

Zuerst die Standardwerte einer Neuinstallation verwenden. Global Mode wählt geeignete App-UIDs, während Kernschutz für Boot und Keystore aktiv bleibt. Optionales Identity Spoofing bleibt aus, bis es im Identity-Bereich aktiviert wird.

Custom-ROM-Nutzer können Auto Identity für eine aktuelle Pixel-beta- oder Canary-Build-Identity aus öffentlichen Google-Metadaten verwenden. Identity Spoof Engine nur aktivieren und neu starten, wenn diese Werte tatsächlich präsentiert werden sollen.

Für DRM-Identifier-Datenschutz eine Application Rule für die Medien-App erstellen und `privacy=isolate` setzen. DRM Keystore Passthrough kann aktiv bleiben, da echter Keystore-Zertifikatspfad und pseudonyme DRM-ID getrennt sind.

## Hilfe und Projektinformationen

Zur Diagnose WebUI Logs oder Android logcat mit Tag `CleveresTricky` verwenden. Details stehen in [Diagnostics](docs/i18n/de.md#diagnostics).

Projektverlauf: [CHANGELOG](docs/i18n/de.md#changelog), Beiträge: [Contributing](docs/i18n/de.md#contributing), Sprachen: [Language Support](docs/i18n/de.md#languages), Theme: [Theme](docs/i18n/de.md#theme).

Die offizielle [Android-Attestation-Dokumentation](https://source.android.com/docs/security/features/keystore/attestation), [Play-Integrity-Verdict-Dokumentation](https://developer.android.com/google/play/integrity/verdicts) und der [KernelSU-Modulguide](https://kernelsu.org/guide/module.html) bleiben die maßgeblichen Quellen.

## Granulare optionale Identitätssteuerung

CleveresTricky löst Device and Build Identity, Attestation Identity, Telephony Identity, Region Identity, Identity Refresh und Security Patch getrennt auf. Security Patch ist unabhängig von Device and Build Identity.

Kern-Keystore-Interception, echte KeyMint- und StrongBox-Private-Key-Operationen, Root-of-Trust-Verarbeitung, Binder-Sicherheit und notwendige Boot-Kompatibilität bleiben davon unabhängig. Die Oberfläche trennt erfassten realen Zustand, konfigurierte Darstellung und effektiv für Apps sichtbaren Zustand.

Security Patch bietet getrennte Richtlinien für System, Vendor und Boot. Profiles können Apps kohärente optionale Einstellungen zuweisen und Effective State inspector zeigt das tatsächliche Resolver-Ergebnis.
