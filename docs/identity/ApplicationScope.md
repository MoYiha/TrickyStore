# Application Scope

**Language:** **English** | [Türkçe](../i18n/tr/identity/ApplicationScope.md) | [简体中文](../i18n/zh-CN/identity/ApplicationScope.md) | [Español](../i18n/es/identity/ApplicationScope.md) | [Deutsch](../i18n/de/identity/ApplicationScope.md) | [Русский](../i18n/ru/identity/ApplicationScope.md) | [Bahasa Indonesia](../i18n/id/identity/ApplicationScope.md) | [हिन्दी](../i18n/hi/identity/ApplicationScope.md) | [العربية](../i18n/ar/identity/ApplicationScope.md)

## Purpose

Application Scope controls which Android application users receive certificate/keybox compatibility or identity properties. Dual scopes allow users to isolate hardware attestation spoofing from system-wide build property modification.

## Target Scopes (`target.txt` and `identity_target.txt`)

- **Keybox Target Scope (`target.txt`)**: Contains exact package names or bounded wildcard rules targeted for custom Keybox attestation and certificate rewriting when Global Keybox Mode is disabled.
- **Identity Target Scope (`identity_target.txt`)**: Contains package names targeted for per-app Identity properties (Build, Telephony, Region) when Global Identity Mode is disabled.

A package is resolved through Android Package Manager to the calling user identifier (UID). Applications that share a UID also share the same process identity in Binder. If one package in that shared identity matches a rule, the decision applies to the shared caller.

## Dual Global Modes

- **Global Keybox Mode**: Targets all non-system applications for Keybox/Attestation spoofing without requiring entries in `target.txt`. System identities and protected infrastructure remain protected.
- **Global Identity Mode**: Applies Build Identity system properties across all apps via system properties. When disabled, Identity applies only to packages in `identity_target.txt` or assigned Profiles.

## Security Patch Decoupling

The Security Patch module operates independently from the Identity engine. It can be toggled and configured on Dashboard without enabling broad Identity property spoofing.

## Live Updates and Safe Lifecycle

Updating target files replaces the trie and cached decisions atomically. Changes requiring a system reboot are staged and flagged with visual indicators in WebUI until the device restarts.

[Return to the project overview](../README.md)
