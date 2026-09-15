# Strong Integrity Leitfaden

**Sprache:** [English](../../../security/StrongIntegrityGuide.md) | [Türkçe](../../tr/security/StrongIntegrityGuide.md) | [简体中文](../../zh-CN/security/StrongIntegrityGuide.md) | [Español](../../es/security/StrongIntegrityGuide.md) | **Deutsch** | [Русский](../../ru/security/StrongIntegrityGuide.md) | [Bahasa Indonesia](../../id/security/StrongIntegrityGuide.md) | [हिन्दी](../../hi/security/StrongIntegrityGuide.md) | [العربية](../../ar/security/StrongIntegrityGuide.md)

Schritte zum Bestehen von Google Play Integrity (`MEETS_STRONG_INTEGRITY`) mit CleveresTricky nach ROM-Typ:

- **Offizielle ROM (Stock)**: CleveresTricky installieren und eine gültige Keybox hinzufügen. Normalerweise sind keine weiteren Einstellungen nötig.
- **Offizielle ROM mit sehr altem Sicherheitspatch**: Im WebUI Dashboard `Security Patch` aktivieren und auf `Auto` setzen.
- **AOSP ROM**: Im WebUI Dashboard `Identity` aktivieren und Fingerprint anpassen (Auto Pixel Identity oder Geräteschablone wählen). *(Hinweis: Identity und automatisches Spoofing können den RAM-Bedarf durch dynamische Eigenschaftsauswertung leicht erhöhen).*
- **Custom ROM**: Custom ROMs werden offiziell nicht unterstützt. Wenn das Keystore defekt ist oder native Hardware-Attestation fehlschlägt, die alte Fallback-Methode prüfen.

**Keybox-Tools und Import:**
- Online-Checker: https://keybox.tryigit.dev/checker
- Keybox-Download & Info: https://keybox.tryigit.dev/
- Import: Über CleveresTricky WebUI → Keybox Manager hochladen. Kein manuelles Kopieren in alte TrickyStore-Verzeichnisse erforderlich.

**Rechtlicher Hinweis & Community-Info:** CleveresTricky ist ein unabhängiges Open-Source-Community-Projekt ohne Verbindung zu Google LLC. Google Play Integrity Regeln und Erkennungen können sich jederzeit unangekündigt ändern; dauerhafte Verifizierungen können nicht garantiert werden. Nur eigene oder autorisierte Schlüssel verwenden.
