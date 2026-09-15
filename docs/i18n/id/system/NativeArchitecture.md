# Native Architecture

**Bahasa:** [English](../../../system/NativeArchitecture.md) | [Türkçe](../../tr/system/NativeArchitecture.md) | [简体中文](../../zh-CN/system/NativeArchitecture.md) | [Español](../../es/system/NativeArchitecture.md) | [Deutsch](../../de/system/NativeArchitecture.md) | [Русский](../../ru/system/NativeArchitecture.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/system/NativeArchitecture.md) | [العربية](../../ar/system/NativeArchitecture.md)

Portable native logic ada di Rust. Tidak ada first-party C; `binder_interceptor.cpp` satu-satunya C++ exception karena private Android libbinder object ABI. Rust Core memvalidasi Binder layout/stream, FD dan kernel-validated copies.

Rust Injector mengelola file, SELinux socket, FD transfer, maps/symbols, ptrace, registers, remote memory, loader dan cleanup. Temporary stack writes dipulihkan dari bounded journal. Exception C++ tidak boleh berkembang.
