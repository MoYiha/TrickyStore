# Identity Refresh

**Язык:** [English](../../../identity/IdentityRefresh.md) | [Türkçe](../../tr/identity/IdentityRefresh.md) | [简体中文](../../zh-CN/identity/IdentityRefresh.md) | [Español](../../es/identity/IdentityRefresh.md) | [Deutsch](../../de/identity/IdentityRefresh.md) | **Русский** | [Bahasa Indonesia](../../id/identity/IdentityRefresh.md) | [हिन्दी](../../hi/identity/IdentityRefresh.md) | [العربية](../../ar/identity/IdentityRefresh.md)

Готовит validated identity на следующий boot без изменения текущего snapshot. Early boot проверяет staged file и атомарно promotes его, после чего Build Properties и service используют одно состояние.

IMEI/ICCID checksum и длины ограничены. Manual edit удаляет старый staged snapshot; выключение Engine/Refresh до boot предотвращает нежелательную promotion.
