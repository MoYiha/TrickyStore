# Profiles

## Purpose

Profiles apply a coherent group of settings in one transaction. They provide known starting points while keeping every individual control available for later adjustment.

## Daily Compatibility

Daily Compatibility is the recommended starting profile. It uses targeted application scope, enables keybox monitoring, keeps the optional telephony layer off, applies the automatic boot property policy, and preserves genuine RKP and DRM paths.

## Default

Default is a conservative attestation setup. It keeps targeted certificate handling and automatic keybox checks active while leaving build identity, telephony, global mode, and optional boot property changes disabled. RKP and DRM passthrough remain enabled.

## Maximum Compatibility

Maximum Compatibility enables global scope, build identity, identity refresh, telephony handling, and userspace boot property compatibility. It disables RKP and DRM passthrough so the widest configured substitution scope can be tested.

This profile changes the most behavior and should be used for focused testing. It does not alter hardware trust state or guarantee a remote verdict.

## Minimal

Minimal stops certificate substitution and active spoofing features while preserving genuine RKP and DRM paths. It is the preferred diagnostic baseline when the source of a regression is unclear.

## Applying a profile

The WebUI sends a bounded profile request to the service. The service validates the name, moves the request into a processing state, updates protected configuration flags, reloads policy, and removes the request. Unknown names are rejected.

Profile application does not replace keyboxes, application lists, templates, or user backups. Reboot after a profile changes early boot identity or property behavior.

[Return to the project overview](../README.md)
