# Web Interface

**Idioma:** [English](../../../system/WebInterface.md) | [Türkçe](../../tr/system/WebInterface.md) | [简体中文](../../zh-CN/system/WebInterface.md) | **Español** | [Deutsch](../../de/system/WebInterface.md) | [Русский](../../ru/system/WebInterface.md) | [Bahasa Indonesia](../../id/system/WebInterface.md) | [हिन्दी](../../hi/system/WebInterface.md) | [العربية](../../ar/system/WebInterface.md)

Ownership runtime fijo: `index.html` markup/CSS base, `bridge.js` native bridge e intents, `policy.js` policy/state y UI propia, `ux.js` presentation/localization/guide/community. No hay CSS runtime separado ni bundles JS por feature.

En móvil hay navegación inferior, controles táctiles, paneles responsive, password visibility, progress y tabs accesibles. WebUI no escucha TCP: usa API nativa del module manager, bridge Rust acotado, queues root-only y validación estricta de path/method/size/time/input.
