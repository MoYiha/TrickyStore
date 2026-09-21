# Поддержка Magisk

**Язык:** [English](../../../system/Magisk.md) | [Türkçe](../../tr/system/Magisk.md) | [简体中文](../../zh-CN/system/Magisk.md) | [Español](../../es/system/Magisk.md) | [Deutsch](../../de/system/Magisk.md) | **Русский** | [Bahasa Indonesia](../../id/system/Magisk.md) | [हिन्दी](../../hi/system/Magisk.md) | [العربية](../../ar/system/Magisk.md)

## Статус поддержки

CleveresTricky официально поддерживает три root-среды, в каждой доступен обычный WebUI:

* **[KernelSU](https://kernelsu.org)** - WebUI открывается кнопкой модуля в приложении-менеджере.
* **[APatch](https://apatch.dev)** - WebUI открывается кнопкой модуля в приложении-менеджере.
* **Magisk** - WebUI открывается кнопкой **Action** модуля через хост-приложение WebUI (см. ниже).

> [!NOTE]
> Современные системы обнаружения и Google Play Integrity активно проверяют userspace-монтирования и root-файлы, поэтому решения уровня ядра могут давать более устойчивую долговременную маскировку. Сравнение архитектур:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## WebUI в Magisk

Magisk сам не реализует интерфейс WebUI модулей, поэтому в Magisk кнопка Action открывает WebUI CleveresTricky внутри **[отдельного хост-приложения WebUI](https://github.com/adivenxnataly/KsuWebUI)**:

1. В приложении Magisk откройте модуль CleveresTricky и нажмите **Action**.
2. При первом запуске лаунчер скачает APK хоста WebUI из его GitHub-релизов и установит его. Скачивание выполняется только один раз.
3. Затем лаунчер открывает хост с id модуля `cleverestricky`, который разрешается в `/data/adb/modules/cleverestricky/webroot` (`index.html` вместе с существующими `bridge.js`, `policy.js` и `ux.js`).

Терминология, так как названия путают:

* **Хост-приложение WebUI** - отдельное хост/контейнер-приложение WebUI. Это **не** KernelSU, и оно **не** устанавливает менеджер root; единственным root-решением на устройстве остается Magisk.
* **CleveresTricky WebUI** - собственный интерфейс модуля в `/data/adb/modules/cleverestricky/webroot`.
* **Root-бэкенд** - собственная native/Rust-среда CleveresTricky (`cleverestrickyd`, бэкенд, `webui_bridge`), одинаковая во всех трех средах. Хост-приложение - лишь WebView-контейнер с root-доступом; существующий путь связи `bridge.js` с `webui_bridge` не изменен.

---

## Ручная настройка

Предпочитаете править файлы вручную или WebUI недоступен? См. отдельное руководство [Ручная настройка](ManualConfiguration.md). Готовый архив отчета:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
Архив будет сохранен в каталоге `/data/adb/cleverestricky/bugreports/`.
