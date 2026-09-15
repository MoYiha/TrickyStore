# Patch Levels

**Idioma:** [English](../../../identity/PatchLevels.md) | [Türkçe](../../tr/identity/PatchLevels.md) | [简体中文](../../zh-CN/identity/PatchLevels.md) | **Español** | [Deutsch](../../de/identity/PatchLevels.md) | [Русский](../../ru/identity/PatchLevels.md) | [Bahasa Indonesia](../../id/identity/PatchLevels.md) | [हिन्दी](../../hi/identity/PatchLevels.md) | [العربية](../../ar/identity/PatchLevels.md)

`security_patch.txt` define reglas System/Vendor/Boot globales y por aplicación. Acepta valores de calendario, `today`, `device_default`, `prop`, `no`; policy v2 ofrece Device, Property, Manual, Automatic y Omit de forma independiente.

Parsing está limitado y una entrada inválida no aplica estado parcial. Automatic usa aritmética de calendario. Esto solo modifica campos soportados de certificados; no instala actualizaciones, no cambia kernel/vendor firmware ni garantiza verdict remoto.
