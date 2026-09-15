# RKP Protection

**Language:** **English** | [Türkçe](../i18n/tr/security/RkpProtection.md) | [简体中文](../i18n/zh-CN/security/RkpProtection.md) | [Español](../i18n/es/security/RkpProtection.md) | [Deutsch](../i18n/de/security/RkpProtection.md) | [Русский](../i18n/ru/security/RkpProtection.md) | [Bahasa Indonesia](../i18n/id/security/RkpProtection.md) | [हिन्दी](../i18n/hi/security/RkpProtection.md) | [العربية](../i18n/ar/security/RkpProtection.md)

## Purpose

Remote Key Provisioning protection keeps Android provisioning infrastructure on the genuine platform path and prevents protected RKP callers from entering certificate-substitution scope.

## Protected callers

Android and Google RKP application packages are always outside substitution scope. Current callers include `com.android.rkpdapp` and `com.google.android.rkpdapp`. Legacy Remote Provisioner callers include `com.android.remoteprovisioner` and `com.google.android.remoteprovisioner`.

System Android user identifiers are excluded before package policy. Unknown package resolution also fails closed. Global Mode therefore cannot turn a Package Manager failure into an RKP infrastructure hook.

## Unified generated-key behavior

RKP infrastructure callers always remain untouched. For targeted application UIDs, successful `generateKey` replies and later `getKeyEntry` certificate reads deliberately use the same certificate-compatibility path. Keeping those paths unified prevents one alias from exposing two different attestation leaf certificates.

The old `rkp_passthrough` switch is retired. Older configurations and backups may still contain the marker for legacy backup compatibility, but it no longer gates generated-key handling and is not exposed as a runtime toggle.

## Profiles

Built-in profiles no longer change RKP behavior. Daily Compatibility, Default, Minimal, and Maximum Compatibility all retain the same always-on RKP infrastructure protection; profile differences apply only to other optional settings such as scope, identity, keybox monitoring, and DRM passthrough.

## Cache behavior

Protected caller decisions use a short bounded cache. Package changes and policy reloads clear relevant state. This avoids repeated Package Manager work while preventing stale decisions from becoming permanent.

## Limits

CleveresTricky does not simulate an RKP server, manufacture provisioning credentials, replace the remote service, or change the hardware provisioning root. The feature protects the genuine Android flow from accidental interception.

If key creation or provisioning behaves differently, restart the affected application and review the service log. The retired RKP marker is not a troubleshooting control.

[Return to the project overview](../README.md)
