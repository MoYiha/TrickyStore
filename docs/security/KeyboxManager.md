# Keybox Manager

**Language:** **English** | [Türkçe](../i18n/tr/security/KeyboxManager.md) | [简体中文](../i18n/zh-CN/security/KeyboxManager.md) | [Español](../i18n/es/security/KeyboxManager.md) | [Deutsch](../i18n/de/security/KeyboxManager.md) | [Русский](../i18n/ru/security/KeyboxManager.md) | [Bahasa Indonesia](../i18n/id/security/KeyboxManager.md) | [हिन्दी](../i18n/hi/security/KeyboxManager.md) | [العربية](../i18n/ar/security/KeyboxManager.md)

## Purpose

Keybox Manager loads, verifies, selects, and monitors authorized attestation key material. It supports one legacy keybox file, multiple XML files, and encrypted CBOX containers.

## Loading and selection

Files in the protected keybox directory are discovered as a bounded set. Application rules can select a specific file. If no application specific file is selected, the active verified pool is used according to the request algorithm.

Files obtained from an explicitly configured secure source are treated as untrusted input until the same verification process completes. Server metadata, refresh intervals, and authentication settings are validated before use.

## Verification

The verifier confirms that every private key matches its leaf certificate. It checks supported algorithms, certificate chain relationships, certificate dates, duplicate or ambiguous material, and revocation information.

Valid key material is activated immediately upon boot without waiting for network connectivity. When Automatic Keybox Check is enabled, revocation verification is performed asynchronously once online, and revoked keys are deactivated. When Automatic Keybox Check is disabled, custom or revoked key material is admitted without revocation enforcement. A pool containing a malformed entry is rejected as a whole so request behavior does not depend on file ordering.

## Automatic checks

Automatic Keybox Check runs independently of Spoof Engine as part of core Keystore protection. The worker uses a bounded schedule and stops when the service shuts down. File observers and a low frequency fallback poll detect updates without continuous directory scanning.

## Operational guidance

Prefer encrypted CBOX files for storage and transfer. Plain XML contains private key material even when filesystem permissions limit access. Never commit a real keybox to source control.

Keep a verified backup before rotating material. After an update, review the WebUI status and restart an application that may have cached an earlier certificate result.

[Return to the project overview](../README.md)
