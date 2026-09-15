# Strong Integrity 指南

**语言:** [English](../../../security/StrongIntegrityGuide.md) | [Türkçe](../../tr/security/StrongIntegrityGuide.md) | **简体中文** | [Español](../../es/security/StrongIntegrityGuide.md) | [Deutsch](../../de/security/StrongIntegrityGuide.md) | [Русский](../../ru/security/StrongIntegrityGuide.md) | [Bahasa Indonesia](../../id/security/StrongIntegrityGuide.md) | [हिन्दी](../../hi/security/StrongIntegrityGuide.md) | [العربية](../../ar/security/StrongIntegrityGuide.md)

通过 CleveresTricky 获得 Google Play Integrity (`MEETS_STRONG_INTEGRITY`) 的各系统快速配置指南：

- **官方原生系统 (Stock ROM)**：安装 CleveresTricky 并添加有效的 Keybox 即可，通常无需调整其他任何设置。
- **安全补丁较旧的官方系统**：在 WebUI 控制面板中开启 `Security Patch`，并设置为 `Auto` 模式。
- **AOSP 系统**：在 WebUI 控制面板中开启 `Identity`，模拟设备指纹（可选择 Auto Pixel Identity 或经过认证的设备模板）。*（注意：Identity 与自动模拟因动态属性处理可能会略微增加内存占用。）*
- **第三方 Custom ROM**：Custom ROM 不受官方正式支持。若系统 Keystore 损坏或原生硬件 attestation 无法正常工作，请改用旧版回退方案。

**Keybox 工具与导入：**
- Keybox 在线检测工具：https://keybox.tryigit.dev/checker
- Keybox 下载与信息：https://keybox.tryigit.dev/
- 导入方式：打开 CleveresTricky WebUI → Keybox Manager 直接上传导入。无需手动将 keybox.xml 复制到旧版 TrickyStore 目录。

**法律免责声明与社区说明：** CleveresTricky 是由社区独立维护的开源研究与兼容性项目，与 Google LLC 无关。Google Play Integrity 策略、服务端风控规则与密钥吊销状态可能随时由 Google 单方面调整，因此无法保证长期有效或提供任何远程结果担保。请仅使用经授权的自有测试密钥。
