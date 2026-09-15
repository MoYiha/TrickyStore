# Automatic Keybox Check

**语言:** [English](../../../security/AutomaticKeyboxCheck.md) | [Türkçe](../../tr/security/AutomaticKeyboxCheck.md) | **简体中文** | [Español](../../es/security/AutomaticKeyboxCheck.md) | [Deutsch](../../de/security/AutomaticKeyboxCheck.md) | [Русский](../../ru/security/AutomaticKeyboxCheck.md) | [Bahasa Indonesia](../../id/security/AutomaticKeyboxCheck.md) | [हिन्दी](../../hi/security/AutomaticKeyboxCheck.md) | [العربية](../../ar/security/AutomaticKeyboxCheck.md)

Automatic Keybox Check 在不持续扫描存储的情况下维护 keybox 与 revocation 状态。正常文件变化由 observer 处理，在某些文件系统上使用低频 fallback。重复错误不会产生重叠 worker。

每次刷新都会重新验证 key/certificate、chain、算法、有效期、歧义和吊销状态。有效的 keybox 在开机和离线环境下立即可用，无需等待网络。吊销检查严格取决于 Automatic Keybox Check：启用时，将在网络可用后异步验证并在发现吊销时安全停用；禁用时，用户可自由使用自定义或被吊销的 keybox。损坏的条目会使整个池被拒绝。缓存按文件数量和大小限制，未变化的已验证文件可复用解析结果。
