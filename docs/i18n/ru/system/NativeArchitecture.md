# Native Architecture

**Язык:** [English](../../../system/NativeArchitecture.md) | [Türkçe](../../tr/system/NativeArchitecture.md) | [简体中文](../../zh-CN/system/NativeArchitecture.md) | [Español](../../es/system/NativeArchitecture.md) | [Deutsch](../../de/system/NativeArchitecture.md) | **Русский** | [Bahasa Indonesia](../../id/system/NativeArchitecture.md) | [हिन्दी](../../hi/system/NativeArchitecture.md) | [العربية](../../ar/system/NativeArchitecture.md)

Portable native logic реализуется на Rust. First-party C отсутствует; `binder_interceptor.cpp` единственная C++ exception для private Android libbinder object ABI. Rust Core валидирует Binder layouts/streams, FD и kernel-validated copies.

Rust Injector управляет files, SELinux socket, FD transfer, maps/symbols, ptrace, registers, remote memory, loader и cleanup. Temporary stack writes восстанавливаются bounded journal. C++ exception не должна расширяться.
