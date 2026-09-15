# Logging and Diagnostics

**语言:** [English](../../../LOG.md) | [Türkçe](../tr/LOG.md) | **简体中文** | [Español](../es/LOG.md) | [Deutsch](../de/LOG.md) | [Русский](../ru/LOG.md) | [Bahasa Indonesia](../id/LOG.md) | [हिन्दी](../hi/LOG.md) | [العربية](../ar/LOG.md)

CleveresTricky 不单独存储明文日志，诊断写入 Android logcat。常用命令是 `adb logcat -s cleverestricky CleveresTricky`。启动时可关注 service 欢迎、bridge/server、Binder interceptor 和 TEE registration 标记。

`TAMPER DETECTED`、Binder ABI validation failure、rejected keybox、injector timeout 需要处理。发布日志前请检查，因为文件名、包名、设备属性和 PID 仍可能敏感。
