# Application Rules

**Язык:** [English](../../../identity/ApplicationRules.md) | [Türkçe](../../tr/identity/ApplicationRules.md) | [简体中文](../../zh-CN/identity/ApplicationRules.md) | [Español](../../es/identity/ApplicationRules.md) | [Deutsch](../../de/identity/ApplicationRules.md) | **Русский** | [Bahasa Indonesia](../../id/identity/ApplicationRules.md) | [हिन्दी](../../hi/identity/ApplicationRules.md) | [العربية](../../ar/identity/ApplicationRules.md)

Назначает приложению template, проверенный локальный keybox или privacy policy. Валидное правило само является target. `inherit` сохраняет глобальную policy, `isolate` создает стабильные app-scoped IMEI/IMSI/ICCID/MEID/phone/serial/attestation identifiers и DRM `deviceUniqueId` pseudonym, `redact` очищает поддерживаемые значения, сохраняя Android permission failures.

Attestation Identity требует активный verified keybox. DRM isolation независим от DRM Keystore Passthrough. Shared UID разрешается детерминированно через Package Manager, а имя из запроса не считается authority. Новое состояние публикуется атомарно и очищает связанные cache.
