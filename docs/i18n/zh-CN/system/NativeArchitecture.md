# Native Architecture

**语言:** [English](../../../system/NativeArchitecture.md) | [Türkçe](../../tr/system/NativeArchitecture.md) | **简体中文** | [Español](../../es/system/NativeArchitecture.md) | [Deutsch](../../de/system/NativeArchitecture.md) | [Русский](../../ru/system/NativeArchitecture.md) | [Bahasa Indonesia](../../id/system/NativeArchitecture.md) | [हिन्दी](../../hi/system/NativeArchitecture.md) | [العربية](../../ar/system/NativeArchitecture.md)

所有可移植 native logic 使用 Rust。没有 first-party C，只有因 private Android libbinder object ABI 必需的 `binder_interceptor.cpp` 作为 first-party C++ 例外。Rust native core 负责 Binder layout/stream validation、FD classification、kernel-validated copy、版本和控制数据解析。

Rust injector 负责参数、日志、文件校验、SELinux socket、随机 abstract socket、FD transfer、maps/symbol、ptrace、register、remote memory、loader 与 cleanup。临时 target stack 写入使用有界 journal 恢复。C++ 例外不能扩张。
