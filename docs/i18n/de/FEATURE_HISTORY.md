# Feature-Historie und frühere Arbeiten

Dieses Dokument hält ausgewählte historische Entwicklungen von CleveresTricky fest und dient als Referenz und Attribution. Es ist für sich genommen keine Behauptung, dass ein anderes Projekt Quellcode kopiert hat.

## Geräteidentität und Attestation

- **#79 — App-spezifische Konfiguration und `ATTESTATION_ID_*` (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
- **#139 — Zufällige Geräteidentität (2026-02-05)**
  https://github.com/tryigit/CleveresTricky/pull/139
- **#871 — App-bezogene Dual-SIM-/Geräteidentitätskontrollen (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/871
  Umfasst IMEI, IMEI2, MEID, IMSI, ICCID, Telefonnummer und Serial sowie App-/Profil-Scope und Runtime-Lifecycle.

## Keybox / Attestation

- **#77 — Verwaltung und Rotation mehrerer Keyboxes (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/77
- **#79 — Keybox-Verifizierung und Attestation-Identität**
  https://github.com/tryigit/CleveresTricky/pull/79

## Native-/Rust-Architektur

- **#876 — Rust/Native-Interceptor-Architektur und Lifecycle (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/876

## Weitere Modul-Features

Dazu gehören Profile/Templates, App-Scope, Hook-Lifecycle, Identity-Isolation/Redaction, RKP-/DRM-bezogene Funktionen, WebUI-Verwaltung und StrongBox/Attestation-Integration.

- #376 — https://github.com/tryigit/CleveresTricky/pull/376
- #476 — https://github.com/tryigit/CleveresTricky/pull/476
- #618 — https://github.com/tryigit/CleveresTricky/pull/618
- #908 — https://github.com/tryigit/CleveresTricky/pull/908
- #909 — https://github.com/tryigit/CleveresTricky/pull/909
- #910 — https://github.com/tryigit/CleveresTricky/pull/910
- #952 — https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — StrongBox-zu-TEE-Weiterleitung und Harmonisierung der Attestation-Sicherheitsstufe**
  https://github.com/tryigit/CleveresTricky/pull/1132
  Diese Änderung wurde später zurückgesetzt und ist daher nicht Bestandteil des aktuellen `master`.

## Historischer Hinweis

Die oben genannten Links verweisen direkt auf öffentliche GitHub-Entwicklungsaufzeichnungen. Ähnliche Funktionen beweisen für sich genommen weder Quellcodekopie noch einen Lizenzverstoß.
