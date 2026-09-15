# Diagnostics

**Idioma:** [English](../../../system/Diagnostics.md) | [Türkçe](../../tr/system/Diagnostics.md) | [简体中文](../../zh-CN/system/Diagnostics.md) | **Español** | [Deutsch](../../de/system/Diagnostics.md) | [Русский](../../ru/system/Diagnostics.md) | [Bahasa Indonesia](../../id/system/Diagnostics.md) | [हिन्दी](../../hi/system/Diagnostics.md) | [العربية](../../ar/system/Diagnostics.md)

Primero revisar version, Spoof Engine, profile, keybox count, target size, RKP, DRM y native feature state en Dashboard, y buscar el primer error en Logs. Si WebUI no inicia, comprobar logcat, daemon, `webroot`, `webui_bridge` por arquitectura y estado del module manager.

Copy Diagnostics en Info & Resources copia un resumen acotado con claves en inglés y una allowlist fija. Incluye version, root environment, native/interceptor state, conteos agregados de keybox/rule, process CPU/RSS y feature flags; excluye logs, nombres de package/keybox, identity values, credentials, server configuration y key material. Revisa el resumen antes de compartirlo porque los feature flags describen la configuración del módulo.

Para aislar, aplicar Minimal y reboot, confirmar genuine path y luego activar gradualmente targeted Spoof Engine, una fuente/regla y funciones opcionales. Effective State inspector muestra regla/perfil, scope, template, keybox ref, privacy, features, patches, RKP/DRM, KeyMint/StrongBox, provider coexistence y reboot requirement, nunca private keys.
