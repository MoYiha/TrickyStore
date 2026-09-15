# Identity Refresh

**Sprache:** [English](../../../identity/IdentityRefresh.md) | [Türkçe](../../tr/identity/IdentityRefresh.md) | [简体中文](../../zh-CN/identity/IdentityRefresh.md) | [Español](../../es/identity/IdentityRefresh.md) | **Deutsch** | [Русский](../../ru/identity/IdentityRefresh.md) | [Bahasa Indonesia](../../id/identity/IdentityRefresh.md) | [हिन्दी](../../hi/identity/IdentityRefresh.md) | [العربية](../../ar/identity/IdentityRefresh.md)

Bereitet eine validierte Identität für den nächsten Boot vor, ohne den aktuellen Snapshot zu ändern. Early Boot validiert Stage-Datei und promoted sie atomar, sodass Build Properties und Service denselben Zustand nutzen.

IMEI/ICCID-Checksums und Längen sind begrenzt. Manuelle Änderungen entfernen alte Stage-Daten; deaktivierter Engine/Refresh verhindert unerwünschte Promotion.
