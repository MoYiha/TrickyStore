# Attestation

**Idioma:** [English](../../../security/Attestation.md) | [Türkçe](../../tr/security/Attestation.md) | [简体中文](../../zh-CN/security/Attestation.md) | **Español** | [Deutsch](../../de/security/Attestation.md) | [Русский](../../ru/security/Attestation.md) | [Bahasa Indonesia](../../id/security/Attestation.md) | [हिन्दी](../../hi/security/Attestation.md) | [العربية](../../ar/security/Attestation.md)

La capa de attestation ofrece compatibilidad controlada de cadenas de certificados para las aplicaciones seleccionadas, manteniendo la creación real de claves de Android y las operaciones criptográficas posteriores.

Los callers de infraestructura RKP permanecen siempre en la ruta genuina de provisioning de Android. Para los UID de aplicaciones objetivo, las respuestas correctas de `generateKey` y las lecturas posteriores de certificados mediante `getKeyEntry` comparten una sola ruta de compatibilidad, evitando que un mismo alias muestre hojas de attestation distintas.

La operación de clave privada sigue realizándose en Android KeyMint o StrongBox. Antes de activar material se validan correspondencia clave/certificado, algoritmo, cadena, vigencia, ambigüedad y revocación. La sustitución de certificados no crea una raíz de confianza hardware, no bloquea físicamente el bootloader ni garantiza un veredicto remoto.
