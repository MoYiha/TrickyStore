# Native Architecture

**Dil:** [English](../../../system/NativeArchitecture.md) | **Türkçe** | [简体中文](../../zh-CN/system/NativeArchitecture.md) | [Español](../../es/system/NativeArchitecture.md) | [Deutsch](../../de/system/NativeArchitecture.md) | [Русский](../../ru/system/NativeArchitecture.md) | [Bahasa Indonesia](../../id/system/NativeArchitecture.md) | [हिन्दी](../../hi/system/NativeArchitecture.md) | [العربية](../../ar/system/NativeArchitecture.md)

Projede portable native logic Rust ile yazılır. First-party C yoktur; yalnız private Android libbinder object ABI sınırı nedeniyle `binder_interceptor.cpp` first-party C++ olarak kalır. Rust native core Binder layout/stream validation, FD classification, kernel-validated copies, process/version parsing ve bounded control parsing yapar.

Rust injector argument, logging, path/file validation, SELinux socket context, random abstract socket, descriptor transfer, maps/symbol resolution, ptrace, registers, remote memory, loader call ve cleanup durumunu yönetir. Temporary target stack write'ları bounded journal ile geri yüklenir. C++ istisnası büyütülemez; güvenle Rust'a taşınabilen her parça Rust'a taşınmalıdır.
