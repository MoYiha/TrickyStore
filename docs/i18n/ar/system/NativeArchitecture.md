# Native Architecture

**اللغة:** [English](../../../system/NativeArchitecture.md) | [Türkçe](../../tr/system/NativeArchitecture.md) | [简体中文](../../zh-CN/system/NativeArchitecture.md) | [Español](../../es/system/NativeArchitecture.md) | [Deutsch](../../de/system/NativeArchitecture.md) | [Русский](../../ru/system/NativeArchitecture.md) | [Bahasa Indonesia](../../id/system/NativeArchitecture.md) | [हिन्दी](../../hi/system/NativeArchitecture.md) | **العربية**

Portable native logic في Rust. لا يوجد first-party C؛ `binder_interceptor.cpp` هو استثناء C++ الوحيد بسبب private Android libbinder object ABI. Rust Core يتحقق من Binder layouts/streams وFD وkernel-validated copies.

Rust Injector يدير files وSELinux socket وFD transfer وmaps/symbols وptrace وregisters وremote memory وloader وcleanup. Temporary stack writes تستعاد من bounded journal. لا يجب توسيع استثناء C++.
