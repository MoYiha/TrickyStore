# Identity Refresh

**भाषा:** [English](../../../identity/IdentityRefresh.md) | [Türkçe](../../tr/identity/IdentityRefresh.md) | [简体中文](../../zh-CN/identity/IdentityRefresh.md) | [Español](../../es/identity/IdentityRefresh.md) | [Deutsch](../../de/identity/IdentityRefresh.md) | [Русский](../../ru/identity/IdentityRefresh.md) | [Bahasa Indonesia](../../id/identity/IdentityRefresh.md) | **हिन्दी** | [العربية](../../ar/identity/IdentityRefresh.md)

Next boot के लिए validated identity तैयार करता है, current snapshot नहीं बदलता। Early boot staged file validate और atomic promote करता है ताकि Build Properties और service एक ही state उपयोग करें।

IMEI/ICCID checksum और lengths bounded हैं। Manual edit old stage हटाता है; boot से पहले Engine/Refresh off unwanted promotion रोकता है।
