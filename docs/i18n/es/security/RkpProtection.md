# RKP Protection

**Idioma:** [English](../../../security/RkpProtection.md) | [Türkçe](../../tr/security/RkpProtection.md) | [简体中文](../../zh-CN/security/RkpProtection.md) | **Español** | [Deutsch](../../de/security/RkpProtection.md) | [Русский](../../ru/security/RkpProtection.md) | [Bahasa Indonesia](../../id/security/RkpProtection.md) | [हिन्दी](../../hi/security/RkpProtection.md) | [العربية](../../ar/security/RkpProtection.md)

La protección Remote Key Provisioning mantiene la infraestructura de provisioning de Android en la ruta genuina de la plataforma. Los paquetes RKP de Android/Google y los Remote Provisioner heredados quedan fuera del alcance de sustitución; los UID del sistema y las resoluciones de paquete desconocidas fallan en modo cerrado.

Los callers de infraestructura RKP nunca se modifican. Para UID de aplicaciones objetivo, `generateKey` y las lecturas posteriores `getKeyEntry` usan una ruta unificada de compatibilidad de certificados, evitando dos hojas de attestation distintas para el mismo alias.

El antiguo switch `rkp_passthrough` está retirado. El marcador puede seguir presente en configs o backups antiguos, pero ya no controla generated-key ni aparece como runtime toggle en WebUI. Los perfiles integrados no cambian el comportamiento RKP: su protección de infraestructura está siempre activa.

CleveresTricky no simula un servidor RKP, no fabrica credenciales de provisioning ni cambia la raíz hardware de provisioning.
