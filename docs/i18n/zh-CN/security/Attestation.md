# Attestation

**语言:** [English](../../../security/Attestation.md) | [Türkçe](../../tr/security/Attestation.md) | **简体中文** | [Español](../../es/security/Attestation.md) | [Deutsch](../../de/security/Attestation.md) | [Русский](../../ru/security/Attestation.md) | [Bahasa Indonesia](../../id/security/Attestation.md) | [हिन्दी](../../hi/security/Attestation.md) | [العربية](../../ar/security/Attestation.md)

认证层为选定应用提供受控的证书链兼容，同时保留 Android 真实的密钥创建和后续密码学操作。

RKP 基础设施调用者始终保持在 Android 原生的配置路径上。对于被选中的应用 UID，成功的 `generateKey` 响应与后续 `getKeyEntry` 证书读取使用同一条证书兼容路径，避免同一 alias 暴露不同的认证叶证书。

私钥操作仍由 Android KeyMint 或 StrongBox 在请求的安全级别中完成。材料启用前会验证密钥/证书匹配、算法、链结构、有效期、歧义和吊销状态。证书替换不能创建硬件信任根、重新锁定 bootloader 或保证远端判定。
