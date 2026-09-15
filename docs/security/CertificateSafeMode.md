# Certificate Safe Mode

**Language:** **English** | [Türkçe](../i18n/tr/security/CertificateSafeMode.md) | [简体中文](../i18n/zh-CN/security/CertificateSafeMode.md) | [Español](../i18n/es/security/CertificateSafeMode.md) | [Deutsch](../i18n/de/security/CertificateSafeMode.md) | [Русский](../i18n/ru/security/CertificateSafeMode.md) | [Bahasa Indonesia](../i18n/id/security/CertificateSafeMode.md) | [हिन्दी](../i18n/hi/security/CertificateSafeMode.md) | [العربية](../i18n/ar/security/CertificateSafeMode.md)

## Current status

Certificate Safe Mode is a legacy configuration concept. The current module does not expose a WebUI switch that disables certificate compatibility because Keystore and TEE protection are core module behavior.

## Runtime behavior

Core Keystore interception stays registered while the module service is healthy. Global Mode and application rules decide scope. Spoof Engine controls identity values only and does not disable certificate handling.

Older installations can still contain the legacy `tee_broken_mode` flag. The service can read that file for migration and compatibility purposes, but core targeting no longer depends on it.

## Troubleshooting

To isolate an application problem, narrow application scope, use passthrough controls where appropriate, or remove the relevant key material in a controlled test environment. Identity Spoof Engine can be disabled independently when testing identity related behavior.

These software controls do not repair a broken hardware trust path and do not modify the physical TEE root of trust.

[Return to the project overview](../README.md)
