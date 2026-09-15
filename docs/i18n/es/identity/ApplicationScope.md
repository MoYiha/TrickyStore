# Application Scope

**Idioma:** [English](../../../identity/ApplicationScope.md) | [Türkçe](../../tr/identity/ApplicationScope.md) | [简体中文](../../zh-CN/identity/ApplicationScope.md) | **Español** | [Deutsch](../../de/identity/ApplicationScope.md) | [Русский](../../ru/identity/ApplicationScope.md) | [Bahasa Indonesia](../../id/identity/ApplicationScope.md) | [हिन्दी](../../hi/identity/ApplicationScope.md) | [العربية](../../ar/identity/ApplicationScope.md)

Application Scope decide qué aplicaciones Android reciben compatibilidad de certificados/Keybox o de identidad. El módulo cuenta con dos archivos de destino y dos modos globales independientes:

- **Destinos de Keybox (`target.txt`)**: Define los paquetes que reciben Keybox personalizado y atestación TEE cuando el Modo Keybox global está desactivado.
- **Destinos de Identidad (`identity_target.txt`)**: Define los paquetes que reciben propiedades de identidad por app (Build, Telephony, Region) cuando el Modo Identidad global está desactivado.
- **Modo Keybox global**: Aplica Keybox personalizado a todas las aplicaciones sin requerir `target.txt`. Las identidades del sistema e infraestructura permanecen protegidas.
- **Modo Identidad global**: Aplica propiedades de Build a nivel de sistema para todo el dispositivo. Cuando está desactivado, la identidad solo afecta a `identity_target.txt` y perfiles asignados.
- **Módulo independiente de Parche de seguridad**: El parche de seguridad se gestiona independientemente del motor de identidad desde el Panel.

Los paquetes con un UID compartido comparten la misma identidad Binder. Las actualizaciones no válidas fallan de forma cerrada y conservan el último estado válido.
