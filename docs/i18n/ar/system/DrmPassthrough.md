# DRM Keystore Passthrough and Identifier Privacy

**اللغة:** [English](../../../system/DrmPassthrough.md) | [Türkçe](../../tr/system/DrmPassthrough.md) | [简体中文](../../zh-CN/system/DrmPassthrough.md) | [Español](../../es/system/DrmPassthrough.md) | [Deutsch](../../de/system/DrmPassthrough.md) | [Русский](../../ru/system/DrmPassthrough.md) | [Bahasa Indonesia](../../id/system/DrmPassthrough.md) | [हिन्दी](../../hi/system/DrmPassthrough.md) | **العربية**

Passthrough يبقي تطبيقات الوسائط المحددة على genuine Android Keystore certificate path. Identifier Privacy يستبدل فقط supported stable-AIDL `deviceUniqueId` لتطبيق `privacy=isolate` باسم مستعار ثابت خاص بالتطبيق دون استخدام genuine DRM ID في الاشتقاق.

`drm_packages.txt` يدعم exact package وbounded wildcard. عند إنشاء المكون الإضافي، يتم التقاط اسم الحزمة وسياق وقت تشغيل المستخدم (multi-user / work-profile). Hook محدود إلى `IDrmFactory` / `IDrmPlugin.getPropertyByteArray("deviceUniqueId")` ولا يغير HIDL أوsecurity level أوlicenses أوprovisioning أوkeys أوsessions أوHDCP أوstring properties. Unexpected ABI يحافظ على original response fail open.
