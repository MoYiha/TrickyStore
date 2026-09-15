# Boot Properties

**اللغة:** [English](../../../identity/BootProperties.md) | [Türkçe](../../tr/identity/BootProperties.md) | [简体中文](../../zh-CN/identity/BootProperties.md) | [Español](../../es/identity/BootProperties.md) | [Deutsch](../../de/identity/BootProperties.md) | [Русский](../../ru/identity/BootProperties.md) | [Bahasa Indonesia](../../id/identity/BootProperties.md) | [हिन्दी](../../hi/identity/BootProperties.md) | **العربية**

Core userspace property view يقلل كشف مؤشرات unlocked/debug/warranty/verified boot/recovery الشائعة. مجموعة properties ثابتة وتطبق قبل Zygote وتبقى فعالة بشكل مستقل عن optional identity.

`boot_props_mode` يتحكم فقط في Build Identity compatibility الاختياري (`auto`, `force`, `disable`) ولا يوقف core protection. لا يعيد قفل bootloader فعليا ولا يصلح verified boot ولا يغير TEE root of trust.
