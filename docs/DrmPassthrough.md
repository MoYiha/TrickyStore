# DRM Passthrough

## Purpose

DRM Passthrough keeps selected media applications on the genuine keystore certificate path. It reduces the chance that attestation compatibility handling changes streaming or protected playback behavior.

## Package policy

The `drm_packages.txt` file accepts exact package names and bounded wildcard rules. The service resolves the calling Android user identifier and evaluates the package set before global or targeted substitution decisions.

When passthrough is enabled and a caller matches the DRM list, certificate substitution is skipped. Other CleveresTricky controls remain available for applications outside that list.

## Defaults

Daily Compatibility, Default, and Minimal enable DRM Passthrough. Maximum Compatibility disables it for controlled tests that intentionally use the widest scope.

The package list has no effect while the dedicated passthrough control is disabled. Changes reload without restarting the service and replace the decision cache with the new rules.

## Safety

Package count, file size, line length, and wildcard form are bounded. Invalid input leaves the previous valid policy active. Unknown package resolution does not become a broad substitution decision.

## Limits

This feature does not implement DRM, create licenses, bypass content protection, or change Widevine security level. It only preserves the original keystore certificate path for selected applications.

If protected playback behaves differently, enable DRM Passthrough, confirm the package is listed, restart the application, and review the log for the caller decision.

[Return to the project overview](../README.md)
