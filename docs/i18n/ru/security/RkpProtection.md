# RKP Protection

**Язык:** [English](../../../security/RkpProtection.md) | [Türkçe](../../tr/security/RkpProtection.md) | [简体中文](../../zh-CN/security/RkpProtection.md) | [Español](../../es/security/RkpProtection.md) | [Deutsch](../../de/security/RkpProtection.md) | **Русский** | [Bahasa Indonesia](../../id/security/RkpProtection.md) | [हिन्दी](../../hi/security/RkpProtection.md) | [العربية](../../ar/security/RkpProtection.md)

Защита Remote Key Provisioning оставляет инфраструктуру provisioning Android на штатном платформенном пути. Пакеты RKP Android/Google и legacy Remote Provisioner всегда исключены из области подмены; системные UID и неизвестное разрешение пакета работают fail closed.

Вызовы инфраструктуры RKP никогда не изменяются. Для целевых UID приложений `generateKey` и последующие ответы `getKeyEntry` используют единый путь совместимости сертификатов, что не позволяет одному alias показывать два разных attestation leaf.

Старый переключатель `rkp_passthrough` выведен из эксплуатации. Маркер может оставаться в старых конфигурациях и backup, но больше не управляет generated-key и не показывается как runtime toggle WebUI. Встроенные Profiles не меняют RKP: защита инфраструктуры всегда включена.

CleveresTricky не эмулирует RKP-сервер, не создаёт provisioning credentials и не меняет аппаратный provisioning root.
