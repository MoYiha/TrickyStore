# Diagnostics

**اللغة:** [English](../../../system/Diagnostics.md) | [Türkçe](../../tr/system/Diagnostics.md) | [简体中文](../../zh-CN/system/Diagnostics.md) | [Español](../../es/system/Diagnostics.md) | [Deutsch](../../de/system/Diagnostics.md) | [Русский](../../ru/system/Diagnostics.md) | [Bahasa Indonesia](../../id/system/Diagnostics.md) | [हिन्दी](../../hi/system/Diagnostics.md) | **العربية**

افحص Dashboard للقيم version وEngine وprofile وkeybox count وtarget size وRKP وDRM وnative features ثم ابحث عن أول error في Logs. إذا لم تعمل WebUI افحص logcat وdaemon و`webroot` وarchitecture-specific `webui_bridge` وحالة manager.

ينسخ Copy Diagnostics في Info & Resources ملخص دعم محدودا بمفاتيح إنجليزية وallowlist ثابتة. يتضمن version وroot environment وnative/interceptor state وإجمالي keybox/rule count وprocess CPU/RSS وfeature flags؛ ولا يتضمن logs أوأسماء package/keybox أوidentity values أوcredentials أوserver configuration أوkey material. راجع الملخص قبل مشاركته لأن feature flags تصف إعدادات الوحدة.

للعزل استخدم Minimal + reboot، تحقق من genuine path ثم فعّل الميزات واحدة واحدة. Effective State يعرض rule/profile وscope وtemplate وkeybox ref وprivacy وfeatures وpatches وRKP/DRM وKeyMint/StrongBox وprovider coexistence وreboot requirement دون private keys.
