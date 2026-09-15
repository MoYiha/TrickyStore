# RKP Protection

**语言:** [English](../../../security/RkpProtection.md) | [Türkçe](../../tr/security/RkpProtection.md) | **简体中文** | [Español](../../es/security/RkpProtection.md) | [Deutsch](../../de/security/RkpProtection.md) | [Русский](../../ru/security/RkpProtection.md) | [Bahasa Indonesia](../../id/security/RkpProtection.md) | [हिन्दी](../../hi/security/RkpProtection.md) | [العربية](../../ar/security/RkpProtection.md)

Remote Key Provisioning 保护会让 Android provisioning 基础设施保持在真实平台路径上。Android/Google RKP 与旧 Remote Provisioner 包始终排除在证书替换范围之外；系统 UID 与无法解析包名的情况也会 fail closed。

RKP 基础设施调用者从不被修改。对于目标应用 UID，`generateKey` 与后续 `getKeyEntry` 证书响应使用统一的兼容路径，从而避免同一 alias 出现两个不同的 attestation leaf。

旧的 `rkp_passthrough` 开关已经退役。旧配置或备份中可以继续存在该标记，但它不再控制 generated-key 行为，也不会作为 WebUI runtime toggle 暴露。内置 Profiles 不再改变 RKP 行为，RKP 基础设施保护始终开启。

CleveresTricky 不模拟 RKP 服务器、不生成 provisioning credential，也不改变硬件 provisioning root。
