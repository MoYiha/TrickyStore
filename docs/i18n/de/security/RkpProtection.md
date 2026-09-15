# RKP Protection

**Sprache:** [English](../../../security/RkpProtection.md) | [Türkçe](../../tr/security/RkpProtection.md) | [简体中文](../../zh-CN/security/RkpProtection.md) | [Español](../../es/security/RkpProtection.md) | **Deutsch** | [Русский](../../ru/security/RkpProtection.md) | [Bahasa Indonesia](../../id/security/RkpProtection.md) | [हिन्दी](../../hi/security/RkpProtection.md) | [العربية](../../ar/security/RkpProtection.md)

Remote-Key-Provisioning-Schutz hält Androids Provisioning-Infrastruktur auf dem echten Plattformpfad. Android/Google-RKP- und alte Remote-Provisioner-Pakete liegen immer außerhalb der Zertifikatsersetzung; System-UIDs und unbekannte Paketauflösung verhalten sich fail closed.

RKP-Infrastruktur-Caller werden nie verändert. Für Ziel-App-UIDs verwenden `generateKey` und spätere `getKeyEntry`-Zertifikatantworten einen einheitlichen Kompatibilitätspfad, damit ein Alias nicht zwei verschiedene Attestation-Leafs zeigt.

Der alte Schalter `rkp_passthrough` ist stillgelegt. Der Marker darf in alten Konfigurationen oder Backups verbleiben, steuert aber Generated-Key-Verhalten nicht mehr und wird nicht als WebUI-Runtime-Toggle angeboten. Eingebaute Profiles ändern RKP nicht; der Infrastrukturschutz ist immer aktiv.

CleveresTricky simuliert keinen RKP-Server, erzeugt keine Provisioning-Credentials und ändert keinen Hardware-Provisioning-Root.
