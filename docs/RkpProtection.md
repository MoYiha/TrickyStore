# RKP Protection

## Purpose

Remote Key Provisioning protection keeps Android provisioning infrastructure and generated key responses on a genuine platform path. It prevents certificate substitution from interfering with system provisioning work.

## Protected callers

Android and Google RKP application packages are always outside substitution scope. Current callers include `com.android.rkpdapp` and `com.google.android.rkpdapp`. Legacy Remote Provisioner callers include `com.android.remoteprovisioner` and `com.google.android.remoteprovisioner`.

System Android user identifiers are excluded before package policy. Unknown package resolution also fails closed. Global mode therefore cannot turn a Package Manager failure into an RKP infrastructure hook.

## Passthrough behavior

When RKP Passthrough is enabled, generated provisioning key responses remain unchanged from Android KeyMint through the application caller. Existing key certificate handling can remain available for explicitly targeted applications.

Daily Compatibility, Default, and Minimal enable RKP Passthrough. Maximum Compatibility disables it for controlled broad scope testing.

## Cache behavior

Protected caller decisions use a short bounded cache. Package changes and policy reloads clear relevant state. This avoids repeated Package Manager work while preventing stale decisions from becoming permanent.

## Limits

CleveresTricky does not simulate an RKP server, manufacture provisioning credentials, replace the remote service, or change the hardware provisioning root. The feature protects the genuine Android flow from accidental interception.

If key creation or provisioning behaves differently, enable RKP Passthrough, restart the affected application, and review the service log.

[Return to the project overview](../README.md)
