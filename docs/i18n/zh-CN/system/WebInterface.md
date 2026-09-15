# Web Interface

**语言:** [English](../../../system/WebInterface.md) | [Türkçe](../../tr/system/WebInterface.md) | **简体中文** | [Español](../../es/system/WebInterface.md) | [Deutsch](../../de/system/WebInterface.md) | [Русский](../../ru/system/WebInterface.md) | [Bahasa Indonesia](../../id/system/WebInterface.md) | [हिन्दी](../../hi/system/WebInterface.md) | [العربية](../../ar/system/WebInterface.md)

WebUI runtime ownership 固定为 `index.html` 静态结构/base CSS，`bridge.js` native bridge/external intents，`policy.js` policy/state API 与 policy-owned UI，`ux.js` general presentation/localization/guide/community UX。没有 standalone runtime CSS，也不增加 feature-specific JS bundle。

移动端使用底部安全区导航、触摸尺寸控件、responsive panels、password visibility、progress state 和 accessible tabs。WebUI 不监听 TCP port；module manager native API 通过有界 Rust bridge 与 root-only queue 调用 service，所有 path/method/size/time/input 都重复验证。
