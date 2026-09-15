# Attestation

**Язык:** [English](../../../security/Attestation.md) | [Türkçe](../../tr/security/Attestation.md) | [简体中文](../../zh-CN/security/Attestation.md) | [Español](../../es/security/Attestation.md) | [Deutsch](../../de/security/Attestation.md) | **Русский** | [Bahasa Indonesia](../../id/security/Attestation.md) | [हिन्दी](../../hi/security/Attestation.md) | [العربية](../../ar/security/Attestation.md)

Слой attestation обеспечивает управляемую совместимость цепочек сертификатов для выбранных приложений, сохраняя настоящую генерацию ключей Android и последующие криптографические операции.

Вызовы инфраструктуры RKP всегда остаются на штатном пути provisioning Android. Для целевых UID приложений успешные ответы `generateKey` и последующее чтение сертификатов через `getKeyEntry` используют единый путь совместимости, чтобы один alias не показывал разные attestation leaf-сертификаты.

Операции с закрытым ключом по-прежнему выполняются Android KeyMint или StrongBox. Перед активацией проверяются соответствие ключа и сертификата, алгоритм, цепочка, срок действия, неоднозначность и revocation. Подмена сертификата не создаёт аппаратный root of trust, не блокирует bootloader физически и не гарантирует удалённый verdict.
