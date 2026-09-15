# Application Scope

**Язык:** [English](../../../identity/ApplicationScope.md) | [Türkçe](../../tr/identity/ApplicationScope.md) | [简体中文](../../zh-CN/identity/ApplicationScope.md) | [Español](../../es/identity/ApplicationScope.md) | [Deutsch](../../de/identity/ApplicationScope.md) | **Русский** | [Bahasa Indonesia](../../id/identity/ApplicationScope.md) | [हिन्दी](../../hi/identity/ApplicationScope.md) | [العربية](../../ar/identity/ApplicationScope.md)

Определяет UID приложений, которым доступна совместимость сертификатов/Keybox или идентичности. Модуль включает два отдельных целевых файла и два глобальных режима:

- **Цели Keybox (`target.txt`)**: Задаёт пакеты, получающие пользовательский Keybox и TEE-аттестацию при выключенном глобальном режиме Keybox.
- **Цели идентичности (`identity_target.txt`)**: Задаёт пакеты для подмены свойств идентичности (Build, Telephony, Region) при выключенном глобальном режиме идентичности.
- **Глобальный режим Keybox**: Применяет Keybox ко всем пользовательским приложениям без необходимости `target.txt`. Системные и инфраструктурные UID защищены.
- **Глобальный режим идентичности**: Применяет Build-свойства на уровне всей системы. В выключенном состоянии затрагивает только `identity_target.txt` и назначенные профили.
- **Независимый патч безопасности**: Патч безопасности настраивается и переключается на Панели независимо от движка идентичности.

Shared UID означает общую Binder identity. Некорректные обновления отклоняются fail-closed.
