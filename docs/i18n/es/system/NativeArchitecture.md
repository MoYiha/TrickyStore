# Native Architecture

**Idioma:** [English](../../../system/NativeArchitecture.md) | [Türkçe](../../tr/system/NativeArchitecture.md) | [简体中文](../../zh-CN/system/NativeArchitecture.md) | **Español** | [Deutsch](../../de/system/NativeArchitecture.md) | [Русский](../../ru/system/NativeArchitecture.md) | [Bahasa Indonesia](../../id/system/NativeArchitecture.md) | [हिन्दी](../../hi/system/NativeArchitecture.md) | [العربية](../../ar/system/NativeArchitecture.md)

Toda lógica native portable se escribe en Rust. No existe first-party C; `binder_interceptor.cpp` es la única excepción C++ por depender del object ABI privado de Android libbinder. Rust core valida Binder layouts/streams, clasifica FDs y realiza copias kernel-validated acotadas.

El injector Rust administra argumentos, ficheros, SELinux socket, FD transfer, maps/symbols, ptrace, registros, memoria remota, loader y cleanup. Las escrituras temporales de stack se restauran con journal acotado. La excepción C++ no debe crecer.
