# Security Model

**Dil:** [English](../../../security/SecurityModel.md) | **Türkçe** | [简体中文](../../zh-CN/security/SecurityModel.md) | [Español](../../es/security/SecurityModel.md) | [Deutsch](../../de/security/SecurityModel.md) | [Русский](../../ru/security/SecurityModel.md) | [Bahasa Indonesia](../../id/security/SecurityModel.md) | [हिन्दी](../../hi/security/SecurityModel.md) | [العربية](../../ar/security/SecurityModel.md)

Root service, işletim sistemi, KernelSU/APatch, installed module files ve explicitly authorized key material local trust boundary'nin trusted parçalarıdır. Applications, Binder content, uploads, remote response, config edit, archive entry, package rule, template, path ve network metadata untrusted input kabul edilir.

Config root gerçek root-owned directory olmalı; sensitive file root-only mode kullanır, symlink reddedilir ve writes atomiktir. Native parser live Binder ABI'yi doğrular, kernel-validated bounded copy sonrası parse eder. Injector yalnız bilinen entry/resume symbol, desteklenen stopped process, executable platform symbol map ve güvenli root-owned library kabul eder. WebUI TCP port açmaz; native queue/bridge fixed API allowlist ve strict bounds kullanır. Hostile root process'e karşı tam güvenlik garanti edilemez.
