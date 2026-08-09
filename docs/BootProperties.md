# Boot Properties

## Purpose

Boot Properties provides an optional userspace property view for application compatibility. It can reduce exposure of common unlocked, debug, warranty, and regional indicators that applications read through Android properties.

## Early boot behavior

Property updates run before Zygote so later application processes inherit a consistent view. Every property name is fixed in code. Every configurable value is bounded and passed as a quoted argument to the property tool.

The Hide Sensitive Props control covers common verified boot state, flash lock, warranty, debug, build type, build tags, secure boot, and original equipment manufacturer unlock indicators. The optional region control applies a small fixed set of country and hardware region properties.

## Policy modes

The `boot_props_mode` setting accepts `auto`, `force`, or `disable`.

Automatic mode skips overlapping property providers and vendor families known to be sensitive to these changes. Force mode applies the configured view even when automatic conflict checks would defer. Disable mode leaves the feature off without deleting the saved controls.

## Safety and recovery

The master Spoof Engine control takes precedence. Disabling it before boot prevents property changes. If the property tool is unavailable or a required input is unsafe, the script logs the condition and leaves the property path unchanged.

If a vendor feature behaves differently, set the mode to `disable` and reboot. Reenable one control at a time after the device returns to a stable state.

## Limits

Userspace properties do not physically relock the bootloader, repair verified boot, rewrite vbmeta, clear hardware fuses, or modify the TEE root of trust.

[Return to the project overview](../README.md)
