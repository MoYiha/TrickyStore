# Spoof Engine

**语言:** [English](../../../identity/SpoofEngine.md) | [Türkçe](../../tr/identity/SpoofEngine.md) | **简体中文** | [Español](../../es/identity/SpoofEngine.md) | [Deutsch](../../de/identity/SpoofEngine.md) | [Русский](../../ru/identity/SpoofEngine.md) | [Bahasa Indonesia](../../id/identity/SpoofEngine.md) | [हिन्दी](../../hi/identity/SpoofEngine.md) | [العربية](../../ar/identity/SpoofEngine.md)

Spoof Engine 是可选 app-facing identity 控制器。即使关闭，核心 Keystore/TEE interception、certificate compatibility、root-of-trust 与 boot protection 仍继续。

开启后可配合各自控制启用 attestation identity、Telephony、Build Identity、Region 与 Identity Refresh。关闭只停止呈现，不删除保存值。App 可能缓存旧值，因此 live change 后可能需要重启 app，Build Identity 变化需要 reboot。
