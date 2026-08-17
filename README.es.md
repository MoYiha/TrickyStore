# CleveresTricky

**Idioma:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | **Español** | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)

CleveresTricky es un módulo para KernelSU y APatch en Android 12-17. Reúne compatibilidad con Android Keystore y attestation, gestión de Keybox/CBOX, selección de aplicaciones, controles opcionales de identidad, niveles de parche y herramientas de privacidad en una sola WebUI móvil.

Empieza con los valores predeterminados y activa únicamente lo que realmente necesites.

## Qué puedes hacer

- Gestionar, verificar, seleccionar y rotar archivos **Keybox/CBOX**.
- Usar Global Mode o aplicar reglas a aplicaciones concretas.
- Configurar de forma opcional la identidad de dispositivo/build, attestation, telefonía, región y security patch.
- Proteger los flujos de Remote Key Provisioning y reducir la exposición de identificadores DRM compatibles sin pretender eludir DRM.
- Crear copias de seguridad, revisar el estado efectivo y recopilar diagnósticos desde la WebUI o la Action del módulo.

## Inicio rápido

1. Descarga el ZIP más reciente desde la página oficial de [Releases](https://github.com/tryigit/CleveresTricky/releases/latest).
2. Instala el ZIP desde KernelSU o APatch mientras Android está en funcionamiento.
3. Abre la WebUI de CleveresTricky desde tu gestor de módulos.
4. Añade únicamente un **Keybox o CBOX** que te pertenezca o para el que tengas autorización de prueba.
5. Mantén primero la configuración predeterminada y activa identidad, reglas de aplicaciones o privacidad solo cuando sea necesario.

El proyecto no incluye ningún Keybox utilizable ni una clave privada de attestation.

## Entorno compatible

- Android **12-17** / API **31-37**
- **ARM64** y **x86-64**
- **KernelSU** y **APatch**

Magisk y la instalación desde recovery no son compatibles.

## Importante

CleveresTricky mejora la ruta de compatibilidad local, pero los resultados remotos siguen dependiendo del dispositivo real, firmware, estado de certificación, Google Play services, políticas del servidor y los datos configurados. No puede garantizar un resultado concreto de Play Integrity o attestation.

No vuelve a bloquear físicamente el bootloader, no reescribe mediciones de verified boot, no cambia el hardware root of trust, no modifica el módem/baseband y no convierte las funciones de privacidad DRM en un bypass de DRM.

Utiliza únicamente configuraciones y credenciales para las que tengas autorización.

## Más información

- [Keybox Manager](docs/KeyboxManager.md) - carga, verificación, selección y comprobaciones de revocación de Keybox/CBOX.
- [Application Scope](docs/ApplicationScope.md) y [Application Rules](docs/ApplicationRules.md) - elige dónde se aplican las funciones.
- [Build Identity](docs/BuildIdentity.md), [Telephony Identity](docs/TelephonyIdentity.md) y [Patch Levels](docs/PatchLevels.md) - controles opcionales de identidad.
- [RKP Protection](docs/RkpProtection.md) y [DRM Privacy](docs/DrmPassthrough.md) - compatibilidad de plataforma y privacidad.
- [Backup and Restore](docs/BackupRestore.md) - copia y restauración cifrada de la configuración.
- [Security Model](docs/SecurityModel.md) e [Installer](docs/Installer.md) - límites de confianza y detalles de instalación.

## ¿Necesitas ayuda?

Usa la página **Logs** de la WebUI o la **Action** del módulo para crear un informe de diagnóstico de emergencia. Revisa el archivo antes de compartirlo, ya que puede contener información del dispositivo y del sistema.

Consulta [Diagnostics](docs/Diagnostics.md) para problemas comunes y pasos de solución.

## Proyecto

[Changelog](CHANGELOG.md) · [Contribuir](CONTRIBUTING.md) · [Idiomas](LANGUAGES.md) · [Licencia](LICENSING.md) · [Telegram](https://t.me/cleverestech)
