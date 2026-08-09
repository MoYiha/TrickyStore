# Spoof Engine

## Purpose

Spoof Engine is the master runtime control for all active compatibility interception. It gives the user one clear place to stop work that would otherwise consume processor time, memory, or Binder resources.

## Runtime behavior

When enabled, the service starts only the interceptors required by the current configuration. Keystore interception is restored when attestation handling is active. Telephony interception is restored only when its dedicated control is also enabled.

When disabled at runtime, the service sends one root authorized control transaction that clears its Binder registrations and parks the injected native hook. Older loaded runtimes use the individual unregister path as a compatibility fallback. Cleanup failures stay pending and are retried instead of being silently forgotten.

When disabled before a reboot, the early boot scripts skip identity and property changes. The service also avoids native injection. This is the lowest overhead operating state short of uninstalling the module.

## State changes

The WebUI writes the master state as a protected flag in the configuration directory. The service observes the change and wakes its runtime controller immediately. Reinstallation is not required.

Applications can cache values they obtained earlier. Restart an affected application after changing the engine state. Reboot when a change involves fingerprint or boot property views because those values are captured early by Android.

## Safety behavior

The disabled state takes precedence over global targeting, telephony controls, profiles, and keybox availability. If configuration initialization fails, interceptors remain disabled rather than starting with an incomplete policy.

Tamper detection also forces a safe service state. In that state the WebUI can present the warning, but native interception does not start.

[Return to the project overview](../README.md)
