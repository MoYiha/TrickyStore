# Manuelle Konfiguration

**Sprache:** [English](../../../system/ManualConfiguration.md) | [Türkçe](../../tr/system/ManualConfiguration.md) | [简体中文](../../zh-CN/system/ManualConfiguration.md) | [Español](../../es/system/ManualConfiguration.md) | **Deutsch** | [Русский](../../ru/system/ManualConfiguration.md) | [Bahasa Indonesia](../../id/system/ManualConfiguration.md) | [हिन्दी](../../hi/system/ManualConfiguration.md) | [العربية](../../ar/system/ManualConfiguration.md)

Diese Anleitung beschreibt die manuelle Konfiguration von CleveresTricky über Dateien ohne WebUI. Die WebUI bleibt der normale Konfigurationsweg; verwenden Sie diese Anleitung, wenn Sie Dateien lieber von Hand bearbeiten oder die WebUI nicht erreichbar ist. Alle Einstellungs- und Richtliniendateien befinden sich im Verzeichnis `/data/adb/cleverestricky/`.

---

## 1. Geltungsbereich und Ziel-Apps

* **`target.txt`**: Paketnamen (eine App pro Zeile) für die Keystore-Attestation-Verschleierung:
  ```text
  com.google.android.gms
  com.google.android.gms.unstable
  com.android.vending
  ```
* **`global_mode`**: Leere Marker-Datei.
  * **Vorhanden**: Globaler Modus aktiv (alle Nicht-System-/Nicht-Root-Apps werden abgefangen).
  * **Nicht vorhanden**: Es werden nur explizit in `target.txt` genannte Apps abgefangen.
  * *Befehl:* `touch /data/adb/cleverestricky/global_mode` (aktivieren) bzw. `rm -f /data/adb/cleverestricky/global_mode` (deaktivieren).

* **`identity_target.txt`**: Zielpakete für die Verschleierung der Geräteidentität und Build-Eigenschaften.

---

## 2. Geräteidentität und Build-Eigenschaften

* **`spoof_build_vars`**: Zu fälschende Geräteeigenschaften im Format `SCHLÜSSEL=WERT`:
  ```properties
  MANUFACTURER=Google
  MODEL=Pixel 8 Pro
  FINGERPRINT=google/husky/husky:14/UQ1A.240105.004/11269998:user/release-keys
  BRAND=google
  PRODUCT=husky
  DEVICE=husky
  RELEASE=14
  ID=UQ1A.240105.004
  INCREMENTAL=11269998
  TYPE=user
  TAGS=release-keys
  ```
* **`security_patch.txt`**: Datum des Sicherheitspatches (z. B. `2026-03-05`). Leer lassen oder entfernen für automatischen Abgleich mit den Systemeigenschaften.
* **`boot_props_mode`**: Steuert die Bootloader-Eigenschaftssimulation (`auto`, `force` oder `disable`).

---

## 3. Hardware-Keybox

* **`keybox.xml`**: Legen Sie Ihre gültige Keybox-XML-Datei direkt unter `/data/adb/cleverestricky/keybox.xml` ab. Achten Sie auf geschützte Berechtigungen (`chmod 600`).
* **`keyboxes/`**: Verzeichnis zum Speichern mehrerer Keybox-Dateien.
* **Uploads überschreiben nie:** Eine abgelegte oder eingefügte Keybox wird unter dem angegebenen Namen gespeichert, oder als `keybox.xml`, wenn kein Name angegeben wurde; ist dieser Name bereits belegt, wird automatisch der nächste freie Name (`keybox2.xml`, `keybox3.xml`, ...) verwendet.
* **`disabled_keyboxes`**: Ausschlussliste für den Pool. Jede Zeile enthält einen `Bereich:Dateiname`-Bezeichner (`keyboxes:keybox2.xml`, `root:keybox.xml`), der mit den im Keybox-Bereich der WebUI angezeigten Dateinamen übereinstimmt. Aufgelistete Keyboxen bleiben sichtbar und verwaltbar, werden aber nie in den Attestierungs-Pool geladen. Die Schaltflächen Deaktivieren und Aktivieren der WebUI lesen und schreiben diese Datei, sodass sie auch manuell gepflegt werden kann.

---

## 4. DRM- und Datenschutzausnahmen

* **`drm_packages.txt`**: Streaming- und Medien-Apps, die von der Keystore-Veränderung ausgenommen werden sollen, um Widevine L1 zu erhalten:
  ```text
  com.netflix.mediaclient
  com.amazon.avod.thirdpartyclient
  com.disney.disneyplus
  ```

---

## 5. Funktionsschalter (Marker-Dateien)

Funktionen werden durch Erstellen der Datei aktiviert (`touch <Datei>`) und durch Löschen deaktiviert (`rm -f <Datei>`):

| Marker-Datei | Wirkung bei Vorhandensein |
| :--- | :--- |
| `spoof_enabled` | Aktiviert die Identitätsverschleierungs-Engine. |
| `spoof_build_identity` | Aktiviert das Fälschen von Build-Eigenschaften. |
| `auto_keybox_check` | Überprüft automatisch Keybox-Gültigkeit und Widerrufsstatus. |
| `drm_passthrough` | Aktiviert den DRM-Passthrough-Schutz für Apps in `drm_packages.txt`. |
| `hide_sensitive_props` | Verbirgt sensible Root-, Debugging- und Bootloader-Statuswerte. |
| `tee_broken_mode` | Legacy-Migrations-/Kompatibilitätsstatus. Bei Vorhandensein behält der Dienst die Legacy-Migrationsverarbeitung bei; der Kernschutz bleibt unverändert und der Fail-Closed-Breaker aktiviert sich separat bei einem Hardware-TEE-Kommunikationsfehler. |
| `debug_logging` | Aktiviert ausführliche Diagnoseprotokolle in `native_runtime.log`. |

---

## Anwenden von Änderungen und Verifikation

### Änderungen anwenden
Änderungen aus der WebUI wirken über den normalen Laufzeitpfad. Falls Konfigurations- oder Marker-Dateien von Hand bearbeitet wurden, wird danach ein Neustart empfohlen:
```sh
su -c "reboot"
```

### Protokolle einsehen
```sh
su -c "cat /data/adb/cleverestricky/native_runtime.log"
```

### Laufende Prozesse prüfen
```sh
su -c "ps -A | grep -E 'cleverestrickyd|cleverestricky_backend'"
```

### Diagnosebericht erstellen
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
(Unter Magisk öffnet die Action-Schaltfläche die WebUI; führen Sie diese Datei daher direkt aus).
Das fertige Archiv wird unter `/data/adb/cleverestricky/bugreports/` abgelegt.
