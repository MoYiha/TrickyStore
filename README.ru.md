# CleveresTricky

**Язык:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | **Русский** | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)
![Architecture](https://img.shields.io/badge/Arch-ARM64%20%7C%20x86--64-0969DA)

CleveresTricky - модуль KernelSU и APatch для Android Keystore, attestation, идентичности устройства и совместимости приложений. Он объединяет контролируемый native runtime и мобильный WebUI, позволяя управлять областью применения, ключевым материалом, идентичностью, уровнями патчей, защитой Remote Key Provisioning и совместимостью DRM в одном месте.

> Это локализованная пользовательская документация. При технических расхождениях канонической считается английская версия.

## Основные возможности

### Управление runtime

[Spoof Engine](docs/i18n/ru.md#spoof-engine) управляет необязательной подменой идентичности. Основная совместимость Keystore и TEE, а также защита boot properties работают независимо, пока сервис модуля исправен.

[Application Scope](docs/i18n/ru.md#application-scope) описывает Targeted Mode, Global Mode, правила пакетов, общие Android UID и обновление кэша без перезагрузки.

[Application Rules](docs/i18n/ru.md#application-rules) описывает шаблоны на уровне приложения, выбор keybox и стабильные privacy identities.

[Profiles](docs/i18n/ru.md#profiles) описывает Daily Compatibility, Default, Maximum Compatibility и Minimal.

### Attestation и идентичность

[Attestation](docs/i18n/ru.md#attestation) объясняет замену цепочки сертификатов, реальные операции KeyMint, StrongBox и границы программной совместимости.

[Certificate Safe Mode](docs/i18n/ru.md#certificate-safe-mode) документирует устаревшую концепцию настройки. Основной targeting больше не зависит от этого переключателя.

[Keybox Manager](docs/i18n/ru.md#keybox-manager) описывает загрузку, проверку, выбор, ротацию, проверку отзыва и мониторинг keybox.

[Automatic Keybox Check](docs/i18n/ru.md#automatic-keybox-check) объясняет ограниченный worker обслуживания и его жизненный цикл.

[Remote Sources](docs/i18n/ru.md#remote-sources) описывает аутентифицированное получение, проверку подписи, refresh policy и обработку ошибок.

[Encrypted Storage](docs/i18n/ru.md#encrypted-storage) описывает контейнеры CBOX, локальные защищенные кэши и безопасную работу с ключевым материалом.

[Patch Levels](docs/i18n/ru.md#patch-levels) описывает поля патчей System, Vendor и Boot с глобальными и приложенческими правилами.

[Build Identity](docs/i18n/ru.md#build-identity) описывает шаблоны устройств, fingerprint, видимые приложениям Build-поля, синхронную активацию на ранней загрузке и Pixel beta Auto Identity для Custom ROM.

[Identity Refresh](docs/i18n/ru.md#identity-refresh) описывает подготовку идентичности на следующую загрузку и согласованность snapshot.

[Telephony Identity](docs/i18n/ru.md#telephony-identity) описывает значения для двух SIM, сохранение решений Android по разрешениям, поддерживаемые API и границы оператора.

### Совместимость платформы

[Boot Properties](docs/i18n/ru.md#boot-properties) объясняет основной userspace-вид boot properties и отдельную политику идентичности.

[Region Properties](docs/i18n/ru.md#region-properties) описывает необязательный ограниченный вид страны и аппаратного региона.

[Provider Coexistence](docs/i18n/ru.md#provider-coexistence) объясняет, как automatic mode не дает перезаписать другой fingerprint provider.

[RKP Protection](docs/i18n/ru.md#rkp-protection) описывает защищенную инфраструктуру Android и настоящий generated-key passthrough.

[DRM Passthrough and Privacy](docs/i18n/ru.md#drm-passthrough) разделяет две функции. Выбранные медиа-приложения могут оставаться на настоящем Keystore certificate path Android, а `privacy=isolate` может заменять поддерживаемый `deviceUniqueId` стабильным псевдонимом для конкретного приложения.

Это не Widevine или DRM bypass. Уровни безопасности, лицензии, provisioning, content keys, сессии, HDCP и строковые свойства не изменяются.

### Интерфейс и эксплуатация

[Web Interface](docs/i18n/ru.md#web-interface) объясняет native transport менеджера модулей, мобильную навигацию, live status, валидацию и доступность.

Встроенные языки WebUI: **English**, **Türkçe**, **简体中文**, **Español**, **Deutsch**, **Русский**, **Bahasa Indonesia**, **हिन्दी** и **العربية**. Каталоги локальны и переключение языка не требует сети.

[Backup and Restore](docs/i18n/ru.md#backup-restore) описывает зашифрованный экспорт, ограниченный импорт и безопасное восстановление.

[Installer](docs/i18n/ru.md#installer) описывает структуру KernelSU/APatch пакета, проверку payload, поддерживаемые устройства и установку.

[Diagnostics](docs/i18n/ru.md#diagnostics) описывает логи, проверки состояния, частые ошибки и последовательную диагностику.

### Инженерные справочники

[Security Model](docs/i18n/ru.md#security-model) документирует границы доверия, защищенные файлы, проверку входных данных и то, чего модуль не обещает.

[Performance](docs/i18n/ru.md#performance) документирует жизненный цикл hook, ограниченные кэши, фоновую работу, CPU и память.

[Building](docs/i18n/ru.md#building) документирует toolchain, проверки и создаваемые артефакты.

[Native Architecture](docs/i18n/ru.md#native-architecture) документирует Rust injector, Rust native core, языковую политику и единственную необходимую Android C++ ABI границу.

## Быстрый старт

1. Скачайте текущий release ZIP с официальной страницы Releases.
2. При необходимости проверить происхождение официальной сборки проверьте `SHA256SUMS` и GitHub build provenance.
3. Откройте KernelSU или APatch при работающем Android.
4. Установите ZIP и перезагрузите устройство.
5. Откройте CleveresTricky WebUI из менеджера модулей.
6. Новая установка запускается с включенным Global Mode и выключенным необязательным identity spoofing.
7. Добавляйте только принадлежащий вам или разрешенный для тестирования ключевой материал.
8. Настраивайте идентичность только при необходимости.
9. Перезагружайтесь после изменения template Build Identity.

В проекте и release-пакете нет пригодного keybox или приватного attestation key.

## Поддерживаемая среда

Поддерживаются Android 12-17, API 31-37, ARM64 и x86 64. Установка выполняется через KernelSU или APatch при запущенном Android.

Magisk и recovery не поддерживаются. Installer останавливает неподдерживаемый путь до появления частично установленного модуля.

## Важные границы

Результат зависит от реального состояния устройства, firmware, сертификации, ключевого материала, Google Play services и удаленной политики. CleveresTricky улучшает локальный путь совместимости, но не гарантирует конкретный remote verdict.

Telephony значения видимы только через поддерживаемые API приложений и не меняют modem, baseband, EFS, физическую SIM или идентичность у оператора.

На современном Android Android ID ограничен подписью приложения, пользователем и устройством в SettingsProvider. Модуль не предоставляет вводящий в заблуждение глобальный Android ID.

Реальная версия kernel не меняется. Boot properties не блокируют физически bootloader, не чинят verified boot, не переписывают vbmeta и не меняют hardware root of trust.

Разблокированный bootloader сам по себе не означает отказ любого DRM. Поведение зависит от устройства, vendor stack, provisioning, security level, firmware и политики сервиса. Текущая DRM-функция ориентирована на privacy, а не bypass.

Внутренние SHA 256 записи обнаруживают отсутствующие, измененные, добавленные, связанные или неожиданные payload и включают tamper lockdown при ошибке. Подлинность официального release подтверждается отдельным digest и GitHub signed build provenance.

## Рекомендуемая первая настройка

Сначала используйте значения новой установки. Global Mode выбирает подходящие UID приложений, а основные Boot и Keystore защиты остаются активны. Необязательный identity spoofing выключен до явного включения в Identity.

Для Custom ROM Auto Identity может получить актуальную Pixel beta или canary Build Identity из открытых метаданных Google и сохранить ее локально. Включайте Identity Spoof Engine и перезагружайтесь только когда эти значения действительно должны быть видимы приложениям.

Для privacy DRM identifier создайте Application Rule для медиа-приложения и установите `privacy=isolate`. DRM Keystore Passthrough может остаться включенным, потому что настоящий путь Keystore certificate и псевдоним DRM ID независимы.

## Помощь и информация о проекте

Для диагностики используйте WebUI Logs или Android logcat с тегом `CleveresTricky`. Подробности в [Diagnostics](docs/i18n/ru.md#diagnostics).

История проекта: [CHANGELOG](docs/i18n/ru.md#changelog), вклад: [Contributing](docs/i18n/ru.md#contributing), языки: [Language Support](docs/i18n/ru.md#languages), тема: [Theme](docs/i18n/ru.md#theme).

Официальные [Android attestation docs](https://source.android.com/docs/security/features/keystore/attestation), [Play Integrity verdict docs](https://developer.android.com/google/play/integrity/verdicts) и [KernelSU module guide](https://kernelsu.org/guide/module.html) остаются авторитетными источниками.

## Гранулярные необязательные элементы идентичности

CleveresTricky отдельно разрешает Device and Build Identity, Attestation Identity, Telephony Identity, Region Identity, Identity Refresh и Security Patch. Security Patch не зависит от Device and Build Identity.

Основная Keystore interception, реальные операции KeyMint/StrongBox private key, root of trust, Binder safety и необходимая boot compatibility не зависят от этих опций. Интерфейс отдельно показывает реальное захваченное состояние, настроенное представление и эффективное состояние для приложения.

Security Patch предоставляет независимые политики для System, Vendor и Boot. Profiles могут назначать приложениям согласованные настройки, а Effective State inspector показывает итог resolver.
