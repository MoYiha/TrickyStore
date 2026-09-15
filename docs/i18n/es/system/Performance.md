# Performance and Memory

**Idioma:** [English](../../../system/Performance.md) | [Türkçe](../../tr/system/Performance.md) | [简体中文](../../zh-CN/system/Performance.md) | **Español** | [Deutsch](../../de/system/Performance.md) | [Русский](../../ru/system/Performance.md) | [Bahasa Indonesia](../../id/system/Performance.md) | [हिन्दी](../../hi/system/Performance.md) | [العربية](../../ar/system/Performance.md)

Core Keystore interception permanece registrado mientras el servicio esté sano. Al apagar Spoof Engine se aparcan trabajos opcionales de identity/DRM/build/region/telephony mientras certificate y boot protection siguen activos. Automatic Keybox Check tiene control propio.

Binder parser usa arrays fijos y descriptor cache de 64 slots. DRM controller y caches de package/rule/certificate/keybox tienen límites estrictos y evitan busy polling. Rust release usa LTO, optimización de tamaño y linking endurecido.
