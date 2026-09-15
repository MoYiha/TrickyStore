# Remote Sources

**اللغة:** [English](../../../system/RemoteSources.md) | [Türkçe](../../tr/system/RemoteSources.md) | [简体中文](../../zh-CN/system/RemoteSources.md) | [Español](../../es/system/RemoteSources.md) | [Deutsch](../../de/system/RemoteSources.md) | [Русский](../../ru/system/RemoteSources.md) | [Bahasa Indonesia](../../id/system/RemoteSources.md) | [हिन्दी](../../hi/system/RemoteSources.md) | **العربية**

يجلب authorized keybox من HTTPS صريح فقط. Host/port/path/timeout/refresh/auth/header/size محدودة، وsecrets لا تظهر في status.

يمكن فرض signature. لا تفعل البيانات قبل signature وXML/CBOX وsize وkeybox وcertificate وrevocation validation. Failed refresh لا يستبدل verified material.
