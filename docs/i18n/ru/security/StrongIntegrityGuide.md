# Руководство по Strong Integrity

**Язык:** [English](../../../security/StrongIntegrityGuide.md) | [Türkçe](../../tr/security/StrongIntegrityGuide.md) | [简体中文](../../zh-CN/security/StrongIntegrityGuide.md) | [Español](../../es/security/StrongIntegrityGuide.md) | [Deutsch](../../de/security/StrongIntegrityGuide.md) | **Русский** | [Bahasa Indonesia](../../id/security/StrongIntegrityGuide.md) | [हिन्दी](../../hi/security/StrongIntegrityGuide.md) | [العربية](../../ar/security/StrongIntegrityGuide.md)

Краткая инструкция по прохождению Google Play Integrity (`MEETS_STRONG_INTEGRITY`) с помощью CleveresTricky по типам прошивок:

- **Официальная стоковая прошивка (Stock ROM)**: Установите CleveresTricky и добавьте действительный Keybox. Обычно больше ничего настраивать не нужно.
- **Официальная прошивка со старым патчем безопасности**: В WebUI Dashboard включите `Security Patch` и установите режим `Auto`.
- **AOSP прошивка**: В WebUI Dashboard включите `Identity`, настройте подмену отпечатка пальца (Auto Pixel Identity или сертифицированный шаблон устройства). *(Примечание: Identity и авто-спуфинг могут незначительно повысить использование RAM из-за динамического сопоставления свойств).*
- **Кастомные прошивки (Custom ROM)**: Кастомные прошивки официально не поддерживаются. Если Keystore поврежден или нативная аппаратная аттестация не работает, используйте старый метод.

**Инструменты и импорт Keybox:**
- Онлайн-проверка (Keybox Checker): https://keybox.tryigit.dev/checker
- Загрузка и информация о Keybox: https://keybox.tryigit.dev/
- Импорт: Откройте CleveresTricky WebUI → Keybox Manager и загрузите файл. Копировать вручную в старые каталоги TrickyStore не требуется.

**Правовое уведомление:** CleveresTricky - независимый проект с открытым исходным кодом, поддерживаемый сообществом CleveresTricky, и не связан с Google LLC. Политики Google Play Integrity и серверные проверки могут изменяться в любое время без предупреждения; постоянное прохождение проверок не гарантируется. Используйте только авторизованные тестовые ключи.
