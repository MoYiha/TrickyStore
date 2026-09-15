# Automatic Keybox Check

**Idioma:** [English](../../../security/AutomaticKeyboxCheck.md) | [Türkçe](../../tr/security/AutomaticKeyboxCheck.md) | [简体中文](../../zh-CN/security/AutomaticKeyboxCheck.md) | **Español** | [Deutsch](../../de/security/AutomaticKeyboxCheck.md) | [Русский](../../ru/security/AutomaticKeyboxCheck.md) | [Bahasa Indonesia](../../id/security/AutomaticKeyboxCheck.md) | [हिन्दी](../../hi/security/AutomaticKeyboxCheck.md) | [العربية](../../ar/security/AutomaticKeyboxCheck.md)

Mantiene keyboxes y estado de revocación actualizados sin escanear continuamente. File observers manejan cambmodern normales y se usa un fallback de baja frecuencia cuando el filesystem lo requiere; errores repetidos no crean workers superpuestos.

Cada refresh repite la validación de clave, cadena, algoritmo, validez, ambigüedad y revocación. El material válido se admite inmediatamente en el arranque y en entornos sin conexión sin esperar a la red. La comprobación de revocación depende de Automatic Keybox Check: al activarse, se verifica en segundo plano en cuanto hay conexión y se retiran claves revocadas; al desactivarse, se pueden usar keyboxes personalizados o revocados sin rechazo. Las cachés están limitadas por número y tamaño de archivos.
