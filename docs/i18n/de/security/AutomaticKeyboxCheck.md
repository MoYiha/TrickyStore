# Automatic Keybox Check

**Sprache:** [English](../../../security/AutomaticKeyboxCheck.md) | [Türkçe](../../tr/security/AutomaticKeyboxCheck.md) | [简体中文](../../zh-CN/security/AutomaticKeyboxCheck.md) | [Español](../../es/security/AutomaticKeyboxCheck.md) | **Deutsch** | [Русский](../../ru/security/AutomaticKeyboxCheck.md) | [Bahasa Indonesia](../../id/security/AutomaticKeyboxCheck.md) | [हिन्दी](../../hi/security/AutomaticKeyboxCheck.md) | [العربية](../../ar/security/AutomaticKeyboxCheck.md)

Hält Keybox- und Revocation-Zustand aktuell, ohne Storage dauerhaft zu scannen. File Observer übernimmt normale Änderungen; ein niedriger Fallback-Takt deckt ungeeignete Dateisysteme ab.

Jeder Refresh validiert Key, Chain, Algorithmus, Datum, Ambiguität und Revocation neu. Gültiges Keybox-Material wird beim Booten und in Offline-Umgebungen sofort ohne Netzwerkverzögerung aktiviert. Die Revocation-Prüfung ist an Automatic Keybox Check gebunden: Ist dieser aktiv, wird der Status im Hintergrund bei verfügbarer Verbindung geprüft und widerrufene Schlüssel werden entfernt; ist er deaktiviert, bleiben benutzerdefinierte oder widerrufene Keyboxen nutzbar. Caches sind nach Dateianzahl und Größe begrenzt.
