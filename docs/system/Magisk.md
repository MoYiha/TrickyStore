# Magisk Support

**Language:** **English** | [Türkçe](../i18n/tr/system/Magisk.md) | [简体中文](../i18n/zh-CN/system/Magisk.md) | [Español](../i18n/es/system/Magisk.md) | [Deutsch](../i18n/de/system/Magisk.md) | [Русский](../i18n/ru/system/Magisk.md) | [Bahasa Indonesia](../i18n/id/system/Magisk.md) | [हिन्दी](../i18n/hi/system/Magisk.md) | [العربية](../i18n/ar/system/Magisk.md)

## Support Statement

CleveresTricky officially supports three root environments with a normal WebUI on each:

* **[KernelSU](https://kernelsu.org)** - WebUI opens from the manager's module button.
* **[APatch](https://apatch.dev)** - WebUI opens from the manager's module button.
* **Magisk** - WebUI opens from the module **Action** button via the WebUI host app (see below).

> [!NOTE]
> Modern application detection frameworks and Google Play Integrity actively inspect userspace mount namespaces, root binaries, and Zygote hooks, so kernel-level solutions can offer stronger long-term concealment. For a comprehensive architectural comparison, read:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## WebUI on Magisk

Magisk does not implement the module WebUI interface itself, so on Magisk the module Action button opens the CleveresTricky WebUI inside a **[standalone WebUI host](https://github.com/adivenxnataly/KsuWebUI)**:

1. In the Magisk app, open the CleveresTricky module and tap **Action**.
2. On first use, the launcher downloads the WebUI host APK from its GitHub releases and installs it. It is only downloaded once.
3. The launcher then opens the host with the module id `cleverestricky`, which resolves to `/data/adb/modules/cleverestricky/webroot` (`index.html` plus the existing `bridge.js`, `policy.js`, and `ux.js`).

Terminology, because the names are confusing:

* **WebUI host app** is a standalone WebUI host/container application. It is **not** KernelSU and it does **not** install any root manager; Magisk remains the only root solution on the device.
* **CleveresTricky WebUI** is the module's own interface in `/data/adb/modules/cleverestricky/webroot`.
* **Root backend** is CleveresTricky's own native/Rust runtime (`cleverestrickyd`, backend, `webui_bridge`), unchanged on all three environments. The host app is only a WebView container with root shell access; the existing `bridge.js` to `webui_bridge` communication path is untouched.

---

## Manual configuration

Prefer editing files by hand, or can't reach the WebUI? See the standalone [Manual Configuration](ManualConfiguration.md) guide. To gather a complete diagnostic archive, run:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
The resulting archive will be created in `/data/adb/cleverestricky/bugreports/`.
