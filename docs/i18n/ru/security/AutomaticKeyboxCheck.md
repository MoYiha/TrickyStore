# Automatic Keybox Check

**Язык:** [English](../../../security/AutomaticKeyboxCheck.md) | [Türkçe](../../tr/security/AutomaticKeyboxCheck.md) | [简体中文](../../zh-CN/security/AutomaticKeyboxCheck.md) | [Español](../../es/security/AutomaticKeyboxCheck.md) | [Deutsch](../../de/security/AutomaticKeyboxCheck.md) | **Русский** | [Bahasa Indonesia](../../id/security/AutomaticKeyboxCheck.md) | [हिन्दी](../../hi/security/AutomaticKeyboxCheck.md) | [العربية](../../ar/security/AutomaticKeyboxCheck.md)

Обновляет keybox/revocation без постоянного сканирования. File observer обрабатывает обычные изменения, низкочастотный fallback покрывает несовместимые filesystem. Повторные ошибки не создают overlapping workers.

Refresh повторяет проверку key, chain, algorithm, validity, ambiguity, revocation. Допустимый keybox активируется сразу при загрузке и в офлайн-среде без ожидания сети. Проверка отзыва строго привязана к Automatic Keybox Check: при включении статус проверяется асинхронно при появлении сети с безопасным удалением отозванных ключей; при отключении допускаются пользовательские или отозванные ключи. Cache ограничен числом и размером файлов.
