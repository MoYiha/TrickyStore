# Diagnostics

**Язык:** [English](../../../system/Diagnostics.md) | [Türkçe](../../tr/system/Diagnostics.md) | [简体中文](../../zh-CN/system/Diagnostics.md) | [Español](../../es/system/Diagnostics.md) | [Deutsch](../../de/system/Diagnostics.md) | **Русский** | [Bahasa Indonesia](../../id/system/Diagnostics.md) | [हिन्दी](../../hi/system/Diagnostics.md) | [العربية](../../ar/system/Diagnostics.md)

Сначала проверить Dashboard: version, Engine, profile, keybox count, target size, RKP, DRM, native features; затем найти первый error в Logs. При недоступной WebUI проверить logcat, daemon, `webroot`, architecture-specific `webui_bridge` и manager state.

Copy Diagnostics в Info & Resources копирует ограниченную support-сводку с английскими ключами и фиксированным allowlist. Она содержит version, root environment, native/interceptor state, агрегированные keybox/rule count, process CPU/RSS и feature flags; не содержит logs, имен package/keybox, identity values, credentials, server configuration или key material. Проверьте сводку перед отправкой, так как feature flags описывают конфигурацию модуля.

Для изоляции использовать Minimal + reboot, проверить genuine path и добавлять функции по одной. Effective State показывает rule/profile, scope, template, keybox ref, privacy, features, patches, RKP/DRM, KeyMint/StrongBox, provider coexistence и reboot requirement без private keys.
