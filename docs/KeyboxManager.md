# Keybox Manager

**Language:** **English** | [Türkçe](i18n/tr.md#keybox-manager) | [简体中文](i18n/zh-CN.md#keybox-manager) | [Español](i18n/es.md#keybox-manager) | [Deutsch](i18n/de.md#keybox-manager) | [Русский](i18n/ru.md#keybox-manager) | [Bahasa Indonesia](i18n/id.md#keybox-manager) | [हिन्दी](i18n/hi.md#keybox-manager) | [العربية](i18n/ar.md#keybox-manager)

## Purpose

Keybox Manager loads, verifies, selects, and monitors authorized attestation key material. It supports one legacy keybox file, multiple XML files, and encrypted CBOX containers.

## Loading and selection

Files in the protected keybox directory are discovered as a bounded set. Application rules can select a specific file. If no application specific file is selected, the active verified pool is used according to the request algorithm.

Files obtained from an explicitly configured secure source are treated as untrusted input until the same verification process completes. Server metadata, refresh intervals, and authentication settings are validated before use.

## Verification

The verifier confirms that every private key matches its leaf certificate. It checks supported algorithms, certificate chain relationships, certificate dates, duplicate or ambiguous material, and revocation information.

New material does not become active when revocation state cannot be established. A pool containing a broken entry is rejected as a whole so request behavior does not depend on file ordering.

## Automatic checks

Auto Keybox Check runs only while Spoof Engine and the dedicated control are enabled. The worker uses a bounded schedule and stops when the engine is paused. File observers and a low frequency fallback poll detect updates without continuous directory scanning.

## Operational guidance

Prefer encrypted CBOX files for storage and transfer. Plain XML contains private key material even when filesystem permissions limit access. Never commit a real keybox to source control.

Keep a verified backup before rotating material. After an update, review the WebUI status and restart an application that may have cached an earlier certificate result.

[Return to the project overview](../README.md)
