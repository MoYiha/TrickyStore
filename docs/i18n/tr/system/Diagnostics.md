# Diagnostics

**Dil:** [English](../../../system/Diagnostics.md) | **Türkçe** | [简体中文](../../zh-CN/system/Diagnostics.md) | [Español](../../es/system/Diagnostics.md) | [Deutsch](../../de/system/Diagnostics.md) | [Русский](../../ru/system/Diagnostics.md) | [Bahasa Indonesia](../../id/system/Diagnostics.md) | [हिन्दी](../../hi/system/Diagnostics.md) | [العربية](../../ar/system/Diagnostics.md)

Önce Dashboard'da version, Spoof Engine, profile, keybox count, target size, RKP, DRM ve native feature state kontrol edilir; Logs ekranında tekrar eden son hata yerine ilk hata aranır. WebUI açılmıyorsa Android logcat, daemon, `webroot`, architecture-specific `webui_bridge` ve module-manager enable durumu kontrol edilir.

Info & Resources içindeki Copy Diagnostics, sabit allowlist kullanan ve anahtarları İngilizce olan sınırlı bir destek özeti kopyalar. Özet version, root environment, native/interceptor state, toplu keybox/rule count, process CPU/RSS ve feature flag içerir; log, package/keybox adı, identity value, credential, server configuration veya key material içermez. Feature flag'ler module configuration'ı anlattığı için paylaşmadan önce özeti gözden geçirin.

Kontrollü izolasyon için Minimal profile ile reboot edin, genuine path'i doğrulayın, ardından targeted Spoof Engine, tek yetkili key source ve tek rule ekleyin; Build Identity, Telephony, Boot Properties veya broad scope'u birer birer açın. Effective State inspector matched rule/profile, scope, template, keybox ref, privacy, feature decisions, patch values, RKP/DRM, genuine KeyMint/StrongBox durumu, provider coexistence ve reboot gereksinimini gösterir, private key göstermez.
