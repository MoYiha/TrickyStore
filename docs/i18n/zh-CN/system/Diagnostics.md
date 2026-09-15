# Diagnostics

**语言:** [English](../../../system/Diagnostics.md) | [Türkçe](../../tr/system/Diagnostics.md) | **简体中文** | [Español](../../es/system/Diagnostics.md) | [Deutsch](../../de/system/Diagnostics.md) | [Русский](../../ru/system/Diagnostics.md) | [Bahasa Indonesia](../../id/system/Diagnostics.md) | [हिन्दी](../../hi/system/Diagnostics.md) | [العربية](../../ar/system/Diagnostics.md)

首先在 Dashboard 检查版本、Spoof Engine、profile、keybox 数量、target size、RKP、DRM 和 native feature state，并在 Logs 中找到首次错误。WebUI 无法启动时检查 logcat、daemon、`webroot`、architecture-specific `webui_bridge` 与 module manager 状态。

Info & Resources 中的 Copy Diagnostics 会复制采用固定 allowlist、键名为英文的有界支持摘要。摘要包含 version、root environment、native/interceptor state、汇总 keybox/rule count、process CPU/RSS 与 feature flags；不包含日志、package/keybox 名称、identity value、credential、server configuration 或 key material。Feature flags 仍会描述 module configuration，因此分享前请先检查内容。

建议使用 Minimal + reboot 建立 genuine baseline，再逐步启用 targeted Spoof Engine、单个授权 key source/rule，以及 Build Identity、Telephony、Boot Properties 或 broad scope。Effective State inspector 会报告 matched rule/profile、scope、template、keybox ref、privacy、feature decisions、patch、RKP/DRM、KeyMint/StrongBox、provider coexistence 和 reboot requirement，但绝不返回私钥。
