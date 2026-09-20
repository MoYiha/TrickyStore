# Magisk-Unterstützung

**Sprache:** [English](../../../system/Magisk.md) | [Türkçe](../../tr/system/Magisk.md) | [简体中文](../../zh-CN/system/Magisk.md) | [Español](../../es/system/Magisk.md) | **Deutsch** | [Русский](../../ru/system/Magisk.md) | [Bahasa Indonesia](../../id/system/Magisk.md) | [हिन्दी](../../hi/system/Magisk.md) | [العربية](../../ar/system/Magisk.md)

## Unterstützungsstatus

CleveresTricky unterstützt offiziell drei Root-Umgebungen, jeweils mit normaler WebUI:

* **[KernelSU](https://kernelsu.org)** - Die WebUI öffnet sich über die Modulseite der Manager-App.
* **[APatch](https://apatch.dev)** - Die WebUI öffnet sich über die Modulseite der Manager-App.
* **Magisk** - Die WebUI öffnet sich über die **Action**-Schaltfläche des Moduls mithilfe der WebUI-Host-App (siehe unten).

> [!NOTE]
> Moderne Erkennungsverfahren und Google Play Integrity prüfen aktiv Userspace-Mount-Namespaces und Root-Binärdateien, sodass Kernel-Lösungen langfristig eine stärkere Verschleierung bieten können. Architekturvergleich:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## WebUI unter Magisk

Magisk stellt die Modul-WebUI-Schnittstelle selbst nicht bereit; daher öffnet die Action-Schaltfläche die CleveresTricky-WebUI innerhalb einer **[eigenständigen WebUI-Host-App](https://github.com/adivenxnataly/KsuWebUI)**:

1. Öffnen Sie in der Magisk-App das CleveresTricky-Modul und tippen Sie auf **Action**.
2. Beim ersten Aufruf lädt der Starter das WebUI-Host-APK aus dessen GitHub-Releases herunter und installiert es. Der Download erfolgt nur einmal.
3. Der Starter öffnet anschließend den Host mit der Modul-ID `cleverestricky`, die zu `/data/adb/modules/cleverestricky/webroot` aufgelöst wird (`index.html` zusammen mit den bestehenden `bridge.js`, `policy.js` und `ux.js`).

Terminologie, da die Namen verwirrend sind:

* **WebUI-Host-App** ist eine eigenständige WebUI-Host-/Container-App. Es ist **nicht** KernelSU und installiert **keinen** Root-Manager; Magisk bleibt die einzige Root-Lösung auf dem Gerät.
* **CleveresTricky-WebUI** ist die modulloeigene Oberfläche unter `/data/adb/modules/cleverestricky/webroot`.
* **Root-Backend** ist CleveresTrickeys eigene native/Rust-Laufzeit (`cleverestrickyd`, Backend, `webui_bridge`), unverändert in allen drei Umgebungen. Die Host-App ist nur ein WebView-Container mit Root-Shell-Zugriff; der bestehende Kommunikationspfad von `bridge.js` zu `webui_bridge` ist unberührt.

---

## Manuelle Konfiguration

Bearbeiten Sie Dateien lieber von Hand oder ist die WebUI nicht erreichbar? Siehe die eigenständige Anleitung [Manuelle Konfiguration](ManualConfiguration.md). Fertiges Diagnosebericht-Archiv:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
Das fertige Archiv wird unter `/data/adb/cleverestricky/bugreports/` abgelegt.
