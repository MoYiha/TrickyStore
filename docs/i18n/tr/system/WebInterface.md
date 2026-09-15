# Web Interface

**Dil:** [English](../../../system/WebInterface.md) | **Türkçe** | [简体中文](../../zh-CN/system/WebInterface.md) | [Español](../../es/system/WebInterface.md) | [Deutsch](../../de/system/WebInterface.md) | [Русский](../../ru/system/WebInterface.md) | [Bahasa Indonesia](../../id/system/WebInterface.md) | [हिन्दी](../../hi/system/WebInterface.md) | [العربية](../../ar/system/WebInterface.md)

Runtime WebUI file ownership sabittir: `index.html` static markup/base CSS, `bridge.js` native KernelSU/APatch bridge ve external intents, `policy.js` policy/state API ve policy-owned dynamic UI, `ux.js` general presentation/localization/guide/community UX sahibidir. Standalone runtime CSS veya feature-specific JS bundle eklenmez; testler `webroot` dışında kalır.

Mobilde tab menu bottom safe-area ile kullanılır; touch-sized controls, responsive panels, password visibility, progress state ve accessible tab state bulunur. WebUI TCP port dinlemez; module manager native API üzerinden bounded Rust bridge kullanır. Request ID randomness, root-only queue, atomic publication, strict file/path/method/size/time bounds ve service-side validation privileged işlemleri korur.
