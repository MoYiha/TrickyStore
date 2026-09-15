# Certificate Safe Mode

**Idioma:** [English](../../../security/CertificateSafeMode.md) | [Türkçe](../../tr/security/CertificateSafeMode.md) | [简体中文](../../zh-CN/security/CertificateSafeMode.md) | **Español** | [Deutsch](../../de/security/CertificateSafeMode.md) | [Русский](../../ru/security/CertificateSafeMode.md) | [Bahasa Indonesia](../../id/security/CertificateSafeMode.md) | [हिन्दी](../../hi/security/CertificateSafeMode.md) | [العربية](../../ar/security/CertificateSafeMode.md)

Es un concepto heredado. La WebUI actual no ofrece un switch para desactivar la compatibilidad core de Keystore/TEE. Global Mode y Application Rules controlan scope; Spoof Engine controla solo identidad.

`tee_broken_mode` puede leerse para migración en instalaciones antiguas, pero el targeting core no depende de él. Para diagnosticar se recomienda reducir scope, usar passthrough apropiado o retirar material de claves en un entorno controlado.
