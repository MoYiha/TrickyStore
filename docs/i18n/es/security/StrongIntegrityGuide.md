# Guía de Strong Integrity

**Idioma:** [English](../../../security/StrongIntegrityGuide.md) | [Türkçe](../../tr/security/StrongIntegrityGuide.md) | [简体中文](../../zh-CN/security/StrongIntegrityGuide.md) | **Español** | [Deutsch](../../de/security/StrongIntegrityGuide.md) | [Русский](../../ru/security/StrongIntegrityGuide.md) | [Bahasa Indonesia](../../id/security/StrongIntegrityGuide.md) | [हिन्दी](../../hi/security/StrongIntegrityGuide.md) | [العربية](../../ar/security/StrongIntegrityGuide.md)

Pasos rápidos para cumplir Google Play Integrity (`MEETS_STRONG_INTEGRITY`) con CleveresTricky según el tipo de ROM:

- **ROM Oficial (Stock)**: Instala CleveresTricky y añade una Keybox válida. Normalmente no se requiere ninguna otra configuración.
- **ROM Oficial con Parche de Seguridad Antiguo**: En el panel de WebUI activa `Security Patch` y establécelo en `Auto`.
- **ROM AOSP**: Activa `Identity` en WebUI, suplanta la huella digital (puedes usar Auto Pixel Identity o una plantilla de dispositivo certificada). *(Nota: Identity y la suplantación automática pueden aumentar ligeramente el uso de RAM debido al emparejamiento dinámico de propiedades).*
- **Custom ROM**: Las Custom ROMs no tienen soporte oficial. Si el Keystore está roto o la atestación nativa no funciona, consulta el método antiguo.

**Herramientas e importación de Keybox:**
- Comprobador en línea (Checker): https://keybox.tryigit.dev/checker
- Descarga e información de Keybox: https://keybox.tryigit.dev/
- Importación: Abre CleveresTricky WebUI → Keybox Manager e importa el archivo. No es necesario copiar manualmente a directorios antiguos de TrickyStore.

**Aviso legal y de la comunidad:** CleveresTricky es un proyecto independiente de código abierto mantenido por la comunidad y no está afiliado con Google LLC. Las políticas y detecciones de Google Play Integrity pueden cambiar en cualquier momento sin previo aviso; no se garantiza un resultado permanente. Utiliza únicamente claves autorizadas.
