# DRM Keystore Passthrough and Identifier Privacy

**Idioma:** [English](../../../system/DrmPassthrough.md) | [Türkçe](../../tr/system/DrmPassthrough.md) | [简体中文](../../zh-CN/system/DrmPassthrough.md) | **Español** | [Deutsch](../../de/system/DrmPassthrough.md) | [Русский](../../ru/system/DrmPassthrough.md) | [Bahasa Indonesia](../../id/system/DrmPassthrough.md) | [हिन्दी](../../hi/system/DrmPassthrough.md) | [العربية](../../ar/system/DrmPassthrough.md)

Keystore Passthrough mantiene apps multimedia seleccionadas en la ruta genuina de certificados Android. Identifier Privacy sustituye únicamente el `deviceUniqueId` compatible sobre stable AIDL para apps `privacy=isolate`, usando un pseudónimo estable por app que no deriva del ID DRM genuino.

`drm_packages.txt` permite paquetes exactos y wildcards acotados. Al crear el plugin se captura el paquete y el contexto de usuario (multi-user / work-profile). El hook de privacidad se limita a `IDrmFactory` y `IDrmPlugin.getPropertyByteArray("deviceUniqueId")`; no modifica HIDL legado, security level, licenses, provisioning, content keys, sessions, HDCP ni string properties. Si la forma esperada no existe, fail open y conserva la respuesta original.
