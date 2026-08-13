# CleveresTricky

**语言：** [English](README.md) | [Türkçe](README.tr.md) | **简体中文** | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)
![Architecture](https://img.shields.io/badge/Arch-ARM64%20%7C%20x86--64-0969DA)

CleveresTricky 是一个面向 Android Keystore、attestation、设备身份和应用兼容性的 KernelSU 与 APatch 模块。它把受控的原生运行时与移动 WebUI 结合在一起，让用户可以在同一处管理应用范围、密钥材料、身份、补丁级别、Remote Key Provisioning 保护和 DRM 兼容性。

> 本文是用户文档的本地化版本。如与技术实现或英文文档存在歧义，以英文文档为准。

## 主要功能

### 运行时控制

[Spoof Engine](docs/i18n/zh-CN.md#spoof-engine) 控制可选的身份替换。只要模块服务健康，核心 Keystore 与 TEE 兼容路径以及 boot property 保护都会独立保持工作。

[Application Scope](docs/i18n/zh-CN.md#application-scope) 说明定向模式、全局模式、包规则、共享 Android UID 和实时缓存更新。

[Application Rules](docs/i18n/zh-CN.md#application-rules) 说明应用专用模板、keybox 选择和稳定的隐私身份。

[Profiles](docs/i18n/zh-CN.md#profiles) 说明 Daily Compatibility、Default、Maximum Compatibility 和 Minimal 预设。

### Attestation 与身份

[Attestation](docs/i18n/zh-CN.md#attestation) 说明证书链替换、真实 KeyMint 操作、StrongBox 行为以及软件兼容层的边界。

[Certificate Safe Mode](docs/i18n/zh-CN.md#certificate-safe-mode) 说明旧版兼容配置。当前核心目标选择不再依赖这个旧开关。

[Keybox Manager](docs/i18n/zh-CN.md#keybox-manager) 说明 keybox 加载、验证、选择、轮换、吊销检查和自动监控。

[Automatic Keybox Check](docs/i18n/zh-CN.md#automatic-keybox-check) 说明有界后台维护任务及其生命周期。

[Remote Sources](docs/i18n/zh-CN.md#remote-sources) 说明带认证的远程获取、签名校验、刷新策略和失败行为。

[Encrypted Storage](docs/i18n/zh-CN.md#encrypted-storage) 说明 CBOX 容器、本地受保护缓存和安全的密钥材料处理方式。

[Patch Levels](docs/i18n/zh-CN.md#patch-levels) 说明 System、Vendor、Boot 补丁字段以及全局和按应用规则。

[Build Identity](docs/i18n/zh-CN.md#build-identity) 说明设备模板、fingerprint、应用可见 Build 字段、同步早期启动激活和供 Custom ROM 用户使用的 Pixel beta Auto Identity 辅助功能。

[Identity Refresh](docs/i18n/zh-CN.md#identity-refresh) 说明下一次启动身份生成和快照一致性。

[Telephony Identity](docs/i18n/zh-CN.md#telephony-identity) 说明双 SIM 值、权限保留、支持的 Android API 和运营商边界。

### 平台兼容性

[Boot Properties](docs/i18n/zh-CN.md#boot-properties) 说明核心 userspace boot property 视图和独立的身份兼容策略。

[Region Properties](docs/i18n/zh-CN.md#region-properties) 说明可选且有界的国家和硬件区域视图。

[Provider Coexistence](docs/i18n/zh-CN.md#provider-coexistence) 说明自动模式如何避免覆盖其他 fingerprint 提供者。

[RKP Protection](docs/i18n/zh-CN.md#rkp-protection) 说明受保护的 Android 基础设施和真实 generated-key passthrough。

[DRM Passthrough and Privacy](docs/i18n/zh-CN.md#drm-passthrough) 说明两个相互独立的 DRM 行为：选定媒体应用可以继续使用 Android 原生 Keystore 证书路径，而配置 `privacy=isolate` 的应用在支持的现代 DRM HAL `deviceUniqueId` 字节数组读取上可以获得稳定的应用级假名。

该功能不是 Widevine 或 DRM 绕过。它不会修改安全级别、许可证、provisioning 消息、内容密钥、会话、HDCP 状态或字符串属性。

### 界面与操作

[Web Interface](docs/i18n/zh-CN.md#web-interface) 说明原生模块管理器传输、移动导航、实时状态、输入验证和无障碍行为。

内置 WebUI 语言包括 **English**、**Türkçe**、**简体中文**、**Español**、**Deutsch**、**Русский**、**Bahasa Indonesia**、**हिन्दी** 和 **العربية**。所有翻译目录都随模块本地提供，切换语言不需要网络连接。

[Backup and Restore](docs/i18n/zh-CN.md#backup-restore) 说明加密导出、有界导入和安全恢复。

[Installer](docs/i18n/zh-CN.md#installer) 说明 KernelSU/APatch 包布局、payload 校验、支持设备和安装流程。

[Diagnostics](docs/i18n/zh-CN.md#diagnostics) 说明日志、状态检查、常见问题和受控排查顺序。

### 工程参考

[Security Model](docs/i18n/zh-CN.md#security-model) 说明信任边界、受保护文件、输入验证和模块明确不承诺的能力。

[Performance](docs/i18n/zh-CN.md#performance) 说明 hook 生命周期、有界缓存、后台工作、CPU 和内存行为。

[Building](docs/i18n/zh-CN.md#building) 说明工具链、验证任务和生成产物。

[Native Architecture](docs/i18n/zh-CN.md#native-architecture) 说明 Rust injector、Rust native core、强制语言策略以及唯一必要的 Android C++ ABI 边界。

## 快速开始

1. 从官方项目 Release 页面下载当前 ZIP。
2. 如需验证官方构建来源，请检查发布的 `SHA256SUMS` 和 GitHub build provenance。
3. Android 正常运行时打开 KernelSU 或 APatch。
4. 安装 ZIP 并重启。
5. 从模块管理器打开 CleveresTricky WebUI。
6. 新安装默认开启 Global Mode，而可选身份 spoofing 默认关闭。
7. 只添加你拥有或被授权测试的密钥材料。
8. 仅在需要时配置身份功能。
9. 修改模板 Build Identity 后重启。

项目和 Release 不包含可用的 keybox 或私有 attestation 密钥。

## 支持环境

CleveresTricky 支持 Android 12 到 Android 17，也就是 API 31 到 37。支持 ARM64 与 x86 64。安装必须在 Android 运行期间通过 KernelSU 或 APatch 完成。

不支持 Magisk 或 Recovery 安装。安装器会提前停止不受支持的路径，而不是留下不完整模块。

## 重要边界

最终结果取决于真实设备状态、固件、认证、密钥材料、Google Play services 和远程策略。CleveresTricky 可以改善本地兼容路径，但不能保证任何特定远程服务的判定结果。

Telephony 值只通过受支持的应用 API 呈现，不会修改 modem、baseband、EFS、实体 SIM 或运营商看到的身份。

现代 Android 的 Android ID 由 SettingsProvider 按应用签名身份、用户和设备进行作用域管理。CleveresTricky 不提供误导性的全局 Android ID 控制。

真实 kernel 版本不会被更改。核心 boot property 视图不会物理锁回 bootloader、修复 verified boot、重写 vbmeta 或改变硬件 root of trust。

解锁 bootloader 并不自动意味着所有 DRM 都不可用。实际行为取决于设备、vendor 实现、provisioning 状态、安全级别、服务策略和固件。本项目当前 DRM 工作的目标是隐私，而不是绕过保护。

模块内部 SHA 256 记录用于检测缺失、修改、注入、链接或意外 payload，并在验证失败时进入 tamper lockdown。官方 Release 的来源可信度则由独立发布摘要和 GitHub 签名的构建 provenance 提供。

## 推荐的首次配置

先使用新安装默认值。Global Mode 选择适用应用 UID，同时核心 boot 和 Keystore 保护保持启用。可选身份 spoofing 在你从 Identity 页面启用前保持关闭。

Custom ROM 用户如需用于 Play Integrity 测试的当前 Build Identity，可以使用 Auto Identity 从 Google 公共元数据获取 Pixel beta 或 canary 身份并保存在本地。只有在确实要呈现这些值时才启用 Identity Spoof Engine 并重启。

如需 DRM 标识符隐私，为媒体应用创建 Application Rule 并设置 `privacy=isolate`。DRM Keystore Passthrough 可以继续开启，因为真实 Keystore 证书路径与假名 DRM 设备标识路径彼此独立。

## 帮助与项目信息

排查问题时请使用 WebUI Logs 页面或带 `CleveresTricky` 标签的 Android logcat。详细步骤见 [Diagnostics](docs/i18n/zh-CN.md#diagnostics)。

项目历史见 [CHANGELOG](docs/i18n/zh-CN.md#changelog)，贡献指南见 [Contributing](docs/i18n/zh-CN.md#contributing)，语言结构见 [Language Support](docs/i18n/zh-CN.md#languages)，主题说明见 [Theme](docs/i18n/zh-CN.md#theme)。

官方 [Android attestation 文档](https://source.android.com/docs/security/features/keystore/attestation)、[Play Integrity verdict 文档](https://developer.android.com/google/play/integrity/verdicts) 和 [KernelSU 模块指南](https://kernelsu.org/guide/module.html) 仍是各自平台的权威来源。

## 细粒度可选身份控制

CleveresTricky 分别解析 Device and Build Identity、Attestation Identity、Telephony Identity、Region Identity、Identity Refresh 和 Security Patch。Security Patch 与 Device and Build Identity 相互独立。

核心 Keystore interception、真实 KeyMint 与 StrongBox 私钥操作、root of trust 处理、Binder 安全和必要 boot 兼容路径都独立于这些可选功能。界面会分别显示捕获到的真实状态、配置的呈现状态和应用最终可见的有效状态。

Security Patch 页面分别为 System、Vendor、Boot 提供独立策略。Profiles 可以向应用分配一致的可选设置，Effective State inspector 会显示运行时 resolver 的最终结果。
