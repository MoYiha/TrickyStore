# Boot Properties

## Purpose

Boot Properties provides the core userspace property view used for application compatibility. It reduces exposure of common unlocked, debug, warranty, verified boot, and recovery indicators that applications read through Android properties.

## Early boot behavior

Core property updates run before Zygote so later application processes inherit a consistent view. Every property name and every core value is fixed in code.

The core view covers common verified boot state, flash lock, warranty, debug, build type, build tags, secure boot, original equipment manufacturer unlock, and recovery boot mode indicators. This protection is always active while the module is installed and its early boot script can use the property tool.

Optional region identity and template build identity remain separate. Those identity values require Spoof Engine.

## Identity compatibility policy

The `boot_props_mode` setting is retained only for optional template build identity compatibility. It accepts `auto`, `force`, or `disable`.

Automatic mode avoids overlapping build identity providers. Force mode applies configured template build identity even when automatic conflict checks would defer. Disable mode skips optional build identity properties. None of these values disables the core boot property protection path.

## Safety and recovery

If the property tool is unavailable, the script logs the condition and leaves the property path unchanged. Unsafe configuration files are ignored or rejected according to the module file safety rules.

The core property view has no WebUI off switch. Identity controls can still be disabled independently without stopping boot protection.

## Limits

Userspace properties do not physically relock the bootloader, repair verified boot, rewrite vbmeta, clear hardware fuses, or modify the TEE root of trust.

[Return to the project overview](../README.md)