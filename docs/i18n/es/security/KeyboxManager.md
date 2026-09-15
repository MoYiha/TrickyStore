# Keybox Manager

**Idioma:** [English](../../../security/KeyboxManager.md) | [Türkçe](../../tr/security/KeyboxManager.md) | [简体中文](../../zh-CN/security/KeyboxManager.md) | **Español** | [Deutsch](../../de/security/KeyboxManager.md) | [Русский](../../ru/security/KeyboxManager.md) | [Bahasa Indonesia](../../id/security/KeyboxManager.md) | [हिन्दी](../../hi/security/KeyboxManager.md) | [العربية](../../ar/security/KeyboxManager.md)

Carga, verifica, selecciona y monitoriza material autorizado de attestation en formato legacy, XML múltiple y CBOX. Application Rules puede seleccionar un archivo específico; los remote sources siguen siendo untrusted hasta pasar la misma verificación local.

Cada private key debe corresponder al leaf certificate. Se comprueban algoritmo, chain, fechas, duplicados/ambigüedad y revocation. El material de clave válido se activa de inmediato en el arranque sin esperar a la red. Con Automatic Keybox Check habilitado, las comprobaciones de revocación se ejecutan en segundo plano; si está deshabilitado, se admite material personalizado o revocado. Un pool con una entrada corrupta se rechaza por completo.
