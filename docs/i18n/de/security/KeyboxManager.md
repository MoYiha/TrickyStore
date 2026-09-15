# Keybox Manager

**Sprache:** [English](../../../security/KeyboxManager.md) | [Türkçe](../../tr/security/KeyboxManager.md) | [简体中文](../../zh-CN/security/KeyboxManager.md) | [Español](../../es/security/KeyboxManager.md) | **Deutsch** | [Русский](../../ru/security/KeyboxManager.md) | [Bahasa Indonesia](../../id/security/KeyboxManager.md) | [हिन्दी](../../hi/security/KeyboxManager.md) | [العربية](../../ar/security/KeyboxManager.md)

Lädt, verifiziert, wählt und überwacht autorisiertes Attestation-Key-Material in Legacy-, XML- und CBOX-Form. App-Regeln können spezifische Dateien referenzieren; Remote-Daten bleiben bis zur lokalen Prüfung untrusted.

Private Key muss Leaf Certificate entsprechen; Algorithmus, Chain, Datum, Duplikate/Ambiguität und Revocation werden geprüft. Gültiges Schlüsselmaterial wird beim Systemstart ohne Warten auf das Netzwerk sofort aktiviert. Bei aktiviertem Automatic Keybox Check wird die Revocation-Prüfung im Hintergrund durchgeführt; bei deaktivierter Prüfung wird auch benutzerdefiniertes oder widerrufenes Material zugelassen. Ein fehlerhafter Pool wird komplett verworfen.
