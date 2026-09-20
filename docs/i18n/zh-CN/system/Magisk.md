# Magisk 支持

**语言:** [English](../../../system/Magisk.md) | [Türkçe](../../tr/system/Magisk.md) | **简体中文** | [Español](../../es/system/Magisk.md) | [Deutsch](../../de/system/Magisk.md) | [Русский](../../ru/system/Magisk.md) | [Bahasa Indonesia](../../id/system/Magisk.md) | [हिन्दी](../../hi/system/Magisk.md) | [العربية](../../ar/system/Magisk.md)

## 支持声明

CleveresTricky 正式支持三种 root 环境，并在每种环境下都提供常规 WebUI：

* **[KernelSU](https://kernelsu.org)** - 从管理器应用的模块按钮打开 WebUI。
* **[APatch](https://apatch.dev)** - 从管理器应用的模块按钮打开 WebUI。
* **Magisk** - 通过 WebUI 宿主应用从模块 **Action** 按钮打开 WebUI（见下文）。

> [!NOTE]
> 现代应用检测框架与 Google Play Integrity 会主动检测用户态挂载命名空间与 root 二进制文件，因此内核级方案可提供更强的长期隐藏能力。架构对比请参阅：
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## 在 Magisk 上使用 WebUI

Magisk 本身不实现模块 WebUI 接口，因此在 Magisk 下 Action 按钮会在 **[独立 WebUI 宿主应用](https://github.com/adivenxnataly/KsuWebUI)** 中打开 CleveresTricky WebUI：

1. 在 Magisk 应用中打开 CleveresTricky 模块，点击 **Action** 按钮。
2. 首次使用时，启动器会从 GitHub releases 下载 WebUI 宿主 APK 并安装。下载仅执行一次。
3. 启动器随后以模块 id `cleverestricky` 打开宿主，该 id 解析为 `/data/adb/modules/cleverestricky/webroot`（`index.html` 及现有的 `bridge.js`、`policy.js` 和 `ux.js`）。

名称容易混淆，特此说明术语：

* **WebUI 宿主应用**是一个独立的 WebUI 宿主/容器应用。它**不是** KernelSU，也**不会**安装任何 root 管理器；设备上唯一的 root 方案仍然是 Magisk。
* **CleveresTricky WebUI** 是位于 `/data/adb/modules/cleverestricky/webroot` 的模块自带界面。
* **Root 后端**是 CleveresTricky 自身的原生/Rust 运行时（`cleverestrickyd`、后端、`webui_bridge`），在三种环境下均保持不变。宿主应用仅是具有 root shell 访问能力的 WebView 容器；现有的 `bridge.js` 到 `webui_bridge` 通信路径未经改动。

---

## 手动配置

偏好手动编辑文件，或无法打开 WebUI？请参阅独立的[手动配置](ManualConfiguration.md)指南。完整诊断归档：
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
诊断压缩包将生成在 `/data/adb/cleverestricky/bugreports/` 目录下。
