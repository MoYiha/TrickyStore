# CleveresTricky

**Idioma:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | **Español** | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)
![Architecture](https://img.shields.io/badge/Arch-ARM64%20%7C%20x86--64-0969DA)

CleveresTricky es un módulo para KernelSU y APatch orientado a Android Keystore, attestation, identidad y compatibilidad de aplicaciones. Combina un runtime nativo controlado con una WebUI móvil para administrar alcance, material de claves, identidad, niveles de parche, protección de Remote Key Provisioning y compatibilidad DRM desde un único lugar.

> Esta es una traducción de la documentación para usuarios. Si existe alguna diferencia técnica, la documentación en inglés es la referencia canónica.

## Capacidades principales

### Control del runtime

[Spoof Engine](docs/i18n/es.md#spoof-engine) controla la sustitución opcional de identidad. La compatibilidad principal de Keystore y TEE y la protección de boot properties permanecen activas de forma independiente mientras el servicio esté sano.

[Application Scope](docs/i18n/es.md#application-scope) explica el modo dirigido, el modo global, las reglas de paquetes, UID compartidos y las actualizaciones de caché en vivo.

[Application Rules](docs/i18n/es.md#application-rules) explica plantillas por aplicación, selección de keybox e identidades privadas estables.

[Profiles](docs/i18n/es.md#profiles) explica los perfiles Daily Compatibility, Default, Maximum Compatibility y Minimal.

### Attestation e identidad

[Attestation](docs/i18n/es.md#attestation) explica sustitución de cadenas de certificados, operaciones KeyMint reales, StrongBox y los límites de una capa de compatibilidad de software.

[Certificate Safe Mode](docs/i18n/es.md#certificate-safe-mode) documenta el concepto de configuración heredado. El targeting principal ya no depende de ese interruptor.

[Keybox Manager](docs/i18n/es.md#keybox-manager) cubre carga, verificación, selección, rotación, comprobación de revocación y monitorización de keyboxes.

[Automatic Keybox Check](docs/i18n/es.md#automatic-keybox-check) explica el worker de mantenimiento acotado y su ciclo de vida.

[Remote Sources](docs/i18n/es.md#remote-sources) cubre descargas autenticadas, firmas, política de actualización y fallos.

[Encrypted Storage](docs/i18n/es.md#encrypted-storage) explica contenedores CBOX, cachés locales protegidas y manejo seguro del material de claves.

[Patch Levels](docs/i18n/es.md#patch-levels) documenta los campos System, Vendor y Boot con reglas globales y por aplicación.

[Build Identity](docs/i18n/es.md#build-identity) explica plantillas de dispositivo, fingerprint, campos Build visibles para las apps, activación temprana sincronizada y el asistente Pixel beta Auto Identity para Custom ROM.

[Identity Refresh](docs/i18n/es.md#identity-refresh) explica la identidad preparada para el siguiente arranque y la consistencia de snapshots.

[Telephony Identity](docs/i18n/es.md#telephony-identity) explica valores para doble SIM, conservación de permisos, APIs Android compatibles y límites del operador.

### Compatibilidad de plataforma

[Boot Properties](docs/i18n/es.md#boot-properties) explica la vista userspace de propiedades de arranque y la política de compatibilidad de identidad separada.

[Region Properties](docs/i18n/es.md#region-properties) explica la vista opcional y acotada de país y región de hardware.

[Provider Coexistence](docs/i18n/es.md#provider-coexistence) explica cómo el modo automático evita sobrescribir otro proveedor de fingerprint.

[RKP Protection](docs/i18n/es.md#rkp-protection) explica la infraestructura Android protegida y el passthrough genuino de generated keys.

[DRM Passthrough and Privacy](docs/i18n/es.md#drm-passthrough) documenta dos controles separados. Las aplicaciones multimedia seleccionadas pueden permanecer en la ruta genuina de certificados Keystore de Android, mientras que `privacy=isolate` puede sustituir el `deviceUniqueId` compatible por un pseudónimo estable específico de la aplicación.

No es un bypass de Widevine ni de DRM. No cambia niveles de seguridad, licencias, provisioning, claves de contenido, sesiones, HDCP ni propiedades de texto.

### Interfaz y operación

[Web Interface](docs/i18n/es.md#web-interface) explica el transporte nativo del gestor, navegación móvil, estado en vivo, validación y accesibilidad.

Los idiomas WebUI integrados son **English**, **Türkçe**, **简体中文**, **Español**, **Deutsch**, **Русский**, **Bahasa Indonesia**, **हिन्दी** y **العربية**. Los catálogos están incluidos localmente y cambiar de idioma no requiere conexión de red.

[Backup and Restore](docs/i18n/es.md#backup-restore) explica exportaciones cifradas, importaciones acotadas y recuperación segura.

[Installer](docs/i18n/es.md#installer) explica el paquete KernelSU/APatch, la verificación de payloads, dispositivos compatibles y el flujo de instalación.

[Diagnostics](docs/i18n/es.md#diagnostics) cubre logs, comprobaciones de estado, fallos comunes y un proceso de diagnóstico controlado.

### Referencias de ingeniería

[Security Model](docs/i18n/es.md#security-model) documenta límites de confianza, archivos protegidos, validación de entradas y capacidades que el módulo no afirma tener.

[Performance](docs/i18n/es.md#performance) documenta ciclo de vida de hooks, cachés acotadas, trabajo en segundo plano, CPU y memoria.

[Building](docs/i18n/es.md#building) documenta toolchain, validaciones y artefactos generados.

[Native Architecture](docs/i18n/es.md#native-architecture) documenta el injector Rust, el core nativo Rust, la política de lenguajes y el único límite Android C++ ABI requerido.

## Inicio rápido

1. Descarga el ZIP actual desde la página oficial de Releases.
2. Si necesitas verificar el origen oficial, comprueba `SHA256SUMS` y la provenance de build de GitHub.
3. Abre KernelSU o APatch con Android en ejecución.
4. Instala el ZIP y reinicia.
5. Abre CleveresTricky WebUI desde el gestor del módulo.
6. Las instalaciones nuevas empiezan con Global Mode activado y el spoofing de identidad opcional desactivado.
7. Añade solo material de claves que poseas o que estés autorizado a probar.
8. Configura opciones de identidad solo cuando sean necesarias.
9. Reinicia tras modificar valores de Build Identity de una plantilla.

El proyecto no incluye un keybox utilizable ni una clave privada de attestation.

## Entorno compatible

CleveresTricky soporta Android 12 a Android 17, API 31 a 37, en ARM64 y x86 64. La instalación se realiza desde KernelSU o APatch mientras Android está funcionando.

Magisk y recovery no son compatibles. El instalador detiene esas rutas antes de dejar un módulo parcial.

## Límites importantes

Los resultados dependen del estado real del dispositivo, firmware, certificación, material de claves, Google Play services y política remota. El módulo mejora la ruta local de compatibilidad pero no garantiza un veredicto remoto concreto.

Los valores de telefonía solo se presentan mediante APIs compatibles. No modifican módem, baseband, EFS, SIM física ni la identidad que ve el operador.

En Android moderno, Android ID está limitado por firma de aplicación, usuario y dispositivo dentro de SettingsProvider. CleveresTricky no presenta un control global engañoso de Android ID.

La versión real del kernel no cambia. La vista de boot properties no vuelve a bloquear físicamente el bootloader, no repara verified boot, no reescribe vbmeta y no cambia el root of trust de hardware.

Un bootloader desbloqueado no implica por sí solo que todo DRM deje de funcionar. El resultado depende del dispositivo, vendor, provisioning, nivel de seguridad, política del servicio y firmware. El trabajo DRM actual está orientado a privacidad, no a bypass.

Los registros SHA 256 internos detectan payloads ausentes, modificados, añadidos, enlazados o inesperados y activan tamper lockdown cuando falla la verificación. La autenticidad de un release oficial se basa en el digest publicado por separado y la provenance firmada por GitHub.

## Primera configuración recomendada

Usa primero los valores predeterminados de una instalación nueva. Global Mode selecciona UID de aplicaciones elegibles mientras las protecciones principales de boot y Keystore permanecen activas. El spoofing de identidad opcional sigue desactivado hasta que lo habilites desde Identity.

Si usas una Custom ROM y necesitas una Build Identity actual para pruebas de Play Integrity, Auto Identity puede obtener una identidad Pixel beta o canary desde metadatos públicos de Google y guardarla localmente. Activa Identity Spoof Engine y reinicia solo cuando quieras exponer esos valores.

Para privacidad del identificador DRM, crea una Application Rule para la aplicación multimedia y usa `privacy=isolate`. DRM Keystore Passthrough puede seguir activado porque la ruta genuina de certificados Keystore y la ruta pseudónima del ID DRM son independientes.

## Ayuda e información del proyecto

Para diagnosticar problemas, usa la pantalla WebUI Logs o Android logcat con la etiqueta `CleveresTricky`. Consulta [Diagnostics](docs/i18n/es.md#diagnostics).

El historial está en [CHANGELOG](docs/i18n/es.md#changelog), la guía de contribución en [Contributing](docs/i18n/es.md#contributing), la estructura de idiomas en [Language Support](docs/i18n/es.md#languages) y el tema en [Theme](docs/i18n/es.md#theme).

La [documentación oficial de Android attestation](https://source.android.com/docs/security/features/keystore/attestation), la [documentación de verdicts de Play Integrity](https://developer.android.com/google/play/integrity/verdicts) y la [guía de módulos KernelSU](https://kernelsu.org/guide/module.html) siguen siendo las referencias autoritativas.

## Controles opcionales granulares de identidad

CleveresTricky resuelve por separado Device and Build Identity, Attestation Identity, Telephony Identity, Region Identity, Identity Refresh y Security Patch. Security Patch es independiente de Device and Build Identity.

La interceptación principal de Keystore, las operaciones privadas reales de KeyMint y StrongBox, root of trust, seguridad Binder y compatibilidad de arranque permanecen independientes de esos controles opcionales. La interfaz separa estado real capturado, estado configurado y estado efectivo visible para la aplicación.

Security Patch ofrece políticas independientes para System, Vendor y Boot. Los perfiles pueden asignar opciones coherentes por aplicación y Effective State inspector muestra el resultado real del resolver.
