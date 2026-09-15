# Native Architecture

**Sprache:** [English](../../../system/NativeArchitecture.md) | [Türkçe](../../tr/system/NativeArchitecture.md) | [简体中文](../../zh-CN/system/NativeArchitecture.md) | [Español](../../es/system/NativeArchitecture.md) | **Deutsch** | [Русский](../../ru/system/NativeArchitecture.md) | [Bahasa Indonesia](../../id/system/NativeArchitecture.md) | [हिन्दी](../../hi/system/NativeArchitecture.md) | [العربية](../../ar/system/NativeArchitecture.md)

Portable native Logik ist Rust. First-party C existiert nicht; `binder_interceptor.cpp` ist wegen des privaten libbinder Object ABI die einzige C++-Ausnahme. Rust Core validiert Binder Layout/Streams, FDs und kernel-validierte Copies.

Der Rust Injector verwaltet Files, SELinux Socket, FD Transfer, Maps/Symbols, ptrace, Register, Remote Memory, Loader und Cleanup. Temporäre Stack-Änderungen werden aus einem begrenzten Journal wiederhergestellt. Die C++-Ausnahme darf nicht wachsen.
