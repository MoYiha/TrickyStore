# Application Rules

**Idioma:** [English](../../../identity/ApplicationRules.md) | [Türkçe](../../tr/identity/ApplicationRules.md) | [简体中文](../../zh-CN/identity/ApplicationRules.md) | **Español** | [Deutsch](../../de/identity/ApplicationRules.md) | [Русский](../../ru/identity/ApplicationRules.md) | [Bahasa Indonesia](../../id/identity/ApplicationRules.md) | [हिन्दी](../../hi/identity/ApplicationRules.md) | [العربية](../../ar/identity/ApplicationRules.md)

Application Rules asigna a una aplicación elegible una plantilla, un keybox local verificado o una política de privacidad. Una regla válida ya es un target explícito. `inherit` conserva la política global; `isolate` deriva IMEI, IMSI, ICCID, MEID, teléfono, serial, identificadores de attestation compatibles y un pseudónimo DRM `deviceUniqueId` estable por aplicación; `redact` devuelve vacíos los identificadores compatibles conservando las denegaciones de permisos de Android.

La identidad de attestation requiere un keybox activo y verificado. DRM isolation es independiente de DRM Keystore Passthrough. Los paquetes con shared UID se resuelven como un contexto determinista usando Package Manager, nunca un nombre de paquete proporcionado por la petición. El estado se publica como snapshot inmutable y limpia las cachés relacionadas.
