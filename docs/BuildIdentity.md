# Build Identity

## Purpose

Build Identity applies a complete device template to the fingerprint and supported app visible Build fields. It is optional and requires a reboot because Android captures these values before normal applications start.

## Template content

A template contains manufacturer, model, brand, product, device, fingerprint, Android release, build identifier, incremental value, build type, build tags, and security patch information. Built in templates can be extended through a validated local template file.

Selecting a template writes the complete supported identity into `spoof_build_vars`. Arbitrary Android properties are rejected. This keeps the feature bounded to fields the module actually consumes.

## Synchronized activation

The early boot script applies the active snapshot before Zygote captures Build fields. The service loads that same snapshot for attestation and application facing identity decisions.

When Refresh Identity on Boot is enabled, the running snapshot is never rotated after early boot. The service prepares a separate randomized snapshot with atomic protected storage. The next early boot phase promotes that prepared snapshot before applying any properties. Build fields and attestation therefore use one synchronized identity for the entire boot.

Manual identity edits discard an older prepared snapshot so a stale random value cannot replace a newer user choice.

## Compatibility policy

Automatic mode detects common overlapping fingerprint providers and leaves Build properties untouched. Other CleveresTricky features can continue operating. Force mode is available for users who intentionally want CleveresTricky to own the property layer.

## Limits

Build Identity changes supported userspace views. It does not change the real hardware model, firmware, kernel, verified boot measurement, or hardware trust root.

[Return to the project overview](../README.md)
