# Automatic Keybox Check

**اللغة:** [English](../../../security/AutomaticKeyboxCheck.md) | [Türkçe](../../tr/security/AutomaticKeyboxCheck.md) | [简体中文](../../zh-CN/security/AutomaticKeyboxCheck.md) | [Español](../../es/security/AutomaticKeyboxCheck.md) | [Deutsch](../../de/security/AutomaticKeyboxCheck.md) | [Русский](../../ru/security/AutomaticKeyboxCheck.md) | [Bahasa Indonesia](../../id/security/AutomaticKeyboxCheck.md) | [हिन्दी](../../hi/security/AutomaticKeyboxCheck.md) | **العربية**

يحافظ على keybox/revocation محدثة دون continuous storage scan. File observer يتعامل مع التغييرات الطبيعية ويستخدم low-frequency fallback عند الحاجة.

كل refresh يعيد فحص key وchain وalgorithm وvalidity وambiguity وrevocation. يتم قبول keybox الصالح فوراً عند الإقلاع وفي وضع عدم الاتصال دون انتظار الشبكة. يرتبط فحص revocation تماماً بإعداد Automatic Keybox Check: عند التفعيل يتم التحقق في الخلفية عند توفر الشبكة وتُستبعد المفاتيح الملغاة؛ وعند التعطيل يُسمح باستخدام keybox مخصص أو ملغى دون رفض. الكاش محدود بعدد وحجم الملفات.
