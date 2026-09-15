# Native Architecture

**भाषा:** [English](../../../system/NativeArchitecture.md) | [Türkçe](../../tr/system/NativeArchitecture.md) | [简体中文](../../zh-CN/system/NativeArchitecture.md) | [Español](../../es/system/NativeArchitecture.md) | [Deutsch](../../de/system/NativeArchitecture.md) | [Русский](../../ru/system/NativeArchitecture.md) | [Bahasa Indonesia](../../id/system/NativeArchitecture.md) | **हिन्दी** | [العربية](../../ar/system/NativeArchitecture.md)

Portable native logic Rust में है। First-party C नहीं है; private Android libbinder object ABI के कारण `binder_interceptor.cpp` एकमात्र C++ exception है। Rust Core Binder layouts/streams, FD और kernel-validated copies validate करता है।

Rust Injector files, SELinux socket, FD transfer, maps/symbols, ptrace, registers, remote memory, loader, cleanup संभालता है। Temporary stack writes bounded journal से restore होते हैं। C++ exception नहीं बढ़नी चाहिए।
