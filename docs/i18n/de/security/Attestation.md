# Attestation

**Sprache:** [English](../../../security/Attestation.md) | [Türkçe](../../tr/security/Attestation.md) | [简体中文](../../zh-CN/security/Attestation.md) | [Español](../../es/security/Attestation.md) | **Deutsch** | [Русский](../../ru/security/Attestation.md) | [Bahasa Indonesia](../../id/security/Attestation.md) | [हिन्दी](../../hi/security/Attestation.md) | [العربية](../../ar/security/Attestation.md)

Die Attestation-Schicht bietet ausgewählten Apps kontrollierte Zertifikatsketten-Kompatibilität, während echte Android-Schlüsselerzeugung und spätere kryptografische Operationen erhalten bleiben.

RKP-Infrastruktur-Caller bleiben immer auf Androids echtem Provisioning-Pfad. Für ausgewählte App-UIDs verwenden erfolgreiche `generateKey`-Antworten und spätere `getKeyEntry`-Zertifikatlesungen denselben Kompatibilitätspfad, damit ein Alias nicht unterschiedliche Attestation-Leaf-Zertifikate zeigt.

Private-Key-Operationen werden weiterhin von Android KeyMint oder StrongBox ausgeführt. Vor Aktivierung werden Schlüssel/Zertifikat-Zuordnung, Algorithmus, Chain, Gültigkeit, Mehrdeutigkeit und Revocation geprüft. Zertifikatsersetzung erzeugt keinen Hardware-Root-of-Trust, sperrt keinen Bootloader physisch und garantiert kein Remote-Verdict.
