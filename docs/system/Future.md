# Future Roadmap

CleveresTricky is built around one rule that should remain true as Android evolves: compatibility features must not silently weaken the platform services they depend on.

This document collects longer-term engineering directions for Android attestation, Remote Key Provisioning, KeyMint, StrongBox, DICE, diagnostics, certificate handling, and platform compatibility. It is a roadmap, not a promise that every item will ship.

The priorities may change as Android, Google Play services, OEM firmware, KeyMint implementations, and attestation infrastructure evolve.

## Design principles

Future work should follow a few basic rules.

- Keep genuine platform security services on their genuine path unless a feature explicitly requires otherwise.
- Never expose private key material to the WebUI, logs, diagnostics, or application processes.
- Prefer hardware-backed operations when the device already provides them.
- Treat TEE, StrongBox, KeyMint, RKP, and DICE as separate trust layers rather than interchangeable labels.
- Fail closed when package identity, security level, certificate ownership, or provisioning state cannot be determined safely.
- Avoid assumptions based on one OEM, one Android release, one certificate-chain length, or one historical attestation root.
- Keep research and compatibility features observable so users can understand which path is active.
- Preserve a clean genuine path for applications and system components that should not be intercepted.

## Remote Key Provisioning

Remote Key Provisioning is becoming increasingly important to Android attestation. CleveresTricky currently protects RKP infrastructure callers from certificate substitution and does not attempt to replace Android's provisioning service.

That behavior should remain the default.

Future RKP work should focus on understanding and exposing the genuine device capabilities rather than simulating production provisioning.

### RKP capability discovery

Add a read-only RKP capability layer that can detect the platform's available remotely provisioned components and report useful public metadata.

Potential information includes:

- Available `IRemotelyProvisionedComponent` instances.
- Primary KeyMint and StrongBox RKP availability.
- HAL or interface version.
- Supported curve and key-generation capabilities.
- Whether the device exposes the newer certificate-request flow.
- Whether RKPD is installed and reachable.
- Whether provisioning appears healthy, unavailable, rate-limited, or temporarily offline.
- Whether a security level is using remotely provisioned or legacy factory attestation credentials where that information can be determined safely.

This should be diagnostic information only. Secret provisioning material must never be logged or exported.

### RKP health inspector

Add an RKP status page to Diagnostics that separates the following states:

- RKP package present.
- RKP system service reachable.
- KeyMint RKP component present.
- StrongBox RKP component present.
- Provisioning request support present.
- Provisioned attestation keys available.
- Temporary provisioning failure.
- Permanent or unsupported state.

The UI should avoid reducing all of these conditions to a single `RKP enabled` flag.

### RKP caller protection

The existing protected-caller model should continue to expand as Android package names and service layouts change.

Future versions should:

- Detect new Android and Google RKPD package names.
- Recognize Mainline package migrations.
- Keep system-user RKP infrastructure outside substitution scope.
- Fail closed when the caller cannot be resolved reliably.
- Clear caller caches when package state changes.
- Add regression tests so Global Mode or future profiles cannot accidentally intercept provisioning infrastructure.

### RKP regression testing

Add automated compatibility tests covering at least:

- Genuine RKPD requests with Spoof Engine disabled.
- Genuine RKPD requests with Spoof Engine enabled globally.
- Targeted application attestation while RKP infrastructure remains untouched.
- TEE-only devices.
- Devices with both TEE and StrongBox.
- Devices without RKP.
- Devices using new Mainline RKP implementations.

The goal is to make RKP protection a tested invariant rather than a package-name exception that can silently regress.

## DICE awareness

DICE is increasingly important to hardware-rooted device identity and Remote Key Provisioning.

CleveresTricky should not attempt to manufacture a production DICE identity. The useful future feature is a DICE inspector that can understand public evidence produced by the device's own secure environment.

### DICE chain inspection

Where the platform exposes suitable public data, future diagnostics may parse and display:

- UDS public identity information.
- DICE certificate-chain length.
- Public-key fingerprints for each layer.
- Signature verification state between DICE layers.
- Security component names where encoded by the implementation.
- Firmware or configuration measurements that are intentionally exposed in the public chain.
- The final KeyMint-related DICE identity.

Private CDIs, UDS secrets, DICE private keys, and other secure-world secrets must never be requested, cached, or exported.

### State comparison

A useful research feature would compare public hardware-backed state before and after meaningful device changes.

Examples:

- OTA update.
- Vendor image update.
- Security patch update.
- Bootloader state change.
- KeyMint or StrongBox firmware change.

The comparison should highlight which public attestation or DICE measurements changed without trying to override them.

This can help identify OEM implementation bugs, stale measurements, inconsistent state propagation, and compatibility regressions.

## KeyMint and StrongBox observability

CleveresTricky already interacts with the Keystore path and can distinguish platform security levels. Future diagnostics should expose this more clearly.

### Security-level matrix

Add a device capability matrix such as:

| Capability | TEE | StrongBox |
| --- | --- | --- |
| KeyMint available | Yes/No | Yes/No |
| Attested key generation | Yes/No | Yes/No |
| RKP component | Yes/No | Yes/No |
| Hardware-backed key | Yes/No | Yes/No |
| Attestation extension observed | Yes/No | Yes/No |
| Provisioning information observed | Yes/No | Yes/No |

The exact fields should be derived from real platform responses rather than guessed from device properties.

### Genuine-key verification

For targeted applications, diagnostics should be able to confirm that the application key itself was genuinely generated by the selected KeyMint security level even when certificate compatibility handling is active.

The report should clearly distinguish:

- Application private key ownership.
- Key security level.
- Original attestation leaf.
- Effective certificate chain returned to the application.
- Selected certificate backend.

No private key bytes should be exposed.

## Certificate and attestation evolution

Android attestation infrastructure changes over time. CleveresTricky should avoid hard-coded historical assumptions.

### Root rotation support

Certificate validation should support legitimate Android attestation root transitions.

Future work should include:

- Multiple trusted historical and current roots where appropriate.
- Root-set updates without requiring large code changes.
- Clear reporting of unknown roots instead of assuming fraud.
- Chain validation based on signatures and trust anchors rather than a fixed certificate count.
- Regression tests for root-transition periods.

### Attestation extension parser

Expand the parser for Android attestation extensions and provisioning information.

Useful fields may include:

- Attestation version.
- KeyMint version.
- Attestation security level.
- KeyMint security level.
- Root of Trust values.
- OS version.
- OS patch level.
- Vendor patch level.
- Boot patch level.
- Application ID information where appropriate.
- Provisioning information such as validated attested entity when present.

Parsing should be version-aware so unknown future fields do not break the entire chain.

### Certificate diff view

Add an advanced diagnostic view showing the difference between the original platform certificate metadata and the effective certificate metadata returned through CleveresTricky.

This should be designed for debugging and should not display private material.

## Legacy keybox lifecycle

Static keybox support will likely become less important as RKP adoption grows, but legacy devices and compatibility use cases will remain for some time.

Future keybox work should prioritize reliability and provenance rather than adding increasingly fragile assumptions.

Potential improvements include:

- Better duplicate detection.
- Public-key fingerprint history.
- More detailed revocation reporting.
- Root-transition awareness.
- Better ambiguity detection when multiple entries could match one request.
- Explicit separation between legacy factory-style credentials and remotely provisioned attestation behavior.
- Clear warnings when a feature depends on an aging attestation model.

The project should not label a static credential as an `RKP keybox`. RKP-provisioned attestation credentials and legacy keybox files are different models and should remain distinct in the UI and documentation.

## Play Integrity compatibility notes

Play Integrity is a server-backed Google service and should not be represented as equivalent to Android Key Attestation.

Future CleveresTricky documentation and diagnostics should keep the distinction explicit.

A locally valid certificate chain does not guarantee any specific Play Integrity verdict. Server-side verdicts may include hardware-backed device state, certified software state, patch freshness, account or environment signals, and other policy that is outside CleveresTricky's certificate layer.

For that reason, the project should avoid promising a permanent `MEETS_STRONG_INTEGRITY` result from any individual compatibility feature.

Useful future work is limited to observable compatibility information, for example:

- Current local KeyMint state.
- Current boot-property configuration.
- Genuine hardware-backed attestation state where available.
- Whether RKP infrastructure is healthy.
- Whether the active configuration changed the application-visible certificate path.

This keeps diagnostics factual even when remote policy changes.

## Research mode

A dedicated Research Mode could collect deeper public metadata without changing runtime behavior.

Possible modules:

### RKP Inspector

Read-only inspection of RKP capabilities and provisioning health.

### DICE Inspector

Parsing and validation of public DICE evidence produced by the genuine platform.

### Attestation Inspector

Detailed parsing of Android attestation and provisioning extensions.

### State Diff

Compare two exported, sanitized snapshots and show which public security properties changed.

### OEM Compatibility Database

Maintain local rules for known OEM implementation differences where those differences are necessary for correct parsing or diagnostics.

Research Mode should be disabled by default and should never export secrets.

## Private RKP laboratory support

For developers studying RKP itself, a future test-only mode may support private laboratory infrastructure.

This would be intended for AOSP test environments, emulators, Cuttlefish, development devices, or explicitly controlled hardware using test credentials and a private trust root.

A laboratory mode could support:

- Test RKP components.
- Private provisioning endpoints.
- Test-only certificate authorities.
- Challenge and certificate lifecycle inspection.
- CBOR and COSE structure debugging.
- Certificate rotation experiments.
- Failure injection and recovery testing.

Production Google provisioning must remain outside this feature. CleveresTricky should not impersonate Google's provisioning service or present a private test root as a production Android trust anchor.

## Privacy and data handling

New diagnostics create new privacy risks even when no private keys are exposed.

Future RKP and DICE features should therefore treat public hardware identifiers carefully.

Rules should include:

- Do not include stable device fingerprints in default support snapshots.
- Hash public keys before displaying them unless the user explicitly requests the full public value.
- Do not upload diagnostic data automatically.
- Keep network access optional and visible.
- Redact challenges, account identifiers, package-specific values, and server responses from shareable logs where practical.
- Allow users to preview every diagnostic bundle before export.

## Android version resilience

Android security interfaces continue to move between framework code, HALs, AIDL interfaces, and Mainline modules.

Future versions should prefer capability discovery over Android-version checks whenever possible.

Instead of assuming:

`Android 16 = feature X`

prefer:

`Interface X exists and reports version Y`

This is especially important for:

- KeyMint.
- StrongBox.
- RKP.
- Mainline RKPD packages.
- Keystore2 Binder interfaces.
- Attestation extension versions.

## Testing infrastructure

The project would benefit from a dedicated compatibility test matrix.

Suggested test classes:

- AOSP emulator without hardware-backed KeyMint.
- Cuttlefish with test RKP support.
- TEE-only retail device.
- TEE plus StrongBox retail device.
- Legacy non-RKP device.
- Android 15 RKP device.
- Android 16 or newer GBL-era device.
- Multiple OEM implementations.

Tests should verify both behavior and non-interference. A feature is not correct if it works for the targeted application but breaks RKPD, DRM, Keystore2, system services, or another security level.

## Proposed internal components

A future implementation could be divided into small components instead of expanding the existing interceptors indefinitely.

Possible structure:

```text
service/
  rkp/
    RkpCapabilities.kt
    RkpInspector.kt
    RkpHealth.kt
    RkpCallerPolicy.kt

  attestation/
    AttestationInspector.kt
    ProvisioningInfoParser.kt
    AttestationDiff.kt

  dice/
    DiceInspector.kt
    DiceChainModel.kt

  keystore/
    SecurityLevelCapabilities.kt

rust/
  src/
    attestation_der.rs
    rkp_cbor.rs
    cose.rs
    dice.rs
```

Names are illustrative. The important goal is to keep parsing, policy, platform access, and UI reporting separated.

## Suggested milestones

### Milestone 1: Better visibility

- RKP capability detection.
- TEE and StrongBox capability matrix.
- Improved attestation extension parsing.
- Root-transition aware certificate validation.
- Sanitized diagnostics output.

### Milestone 2: RKP diagnostics

- RKPD health status.
- IRPC version and capability reporting.
- RKP failure classification.
- Regression tests for protected callers.

### Milestone 3: DICE research support

- Public DICE chain parser.
- Signature validation.
- Public measurement display.
- Snapshot and diff support.

### Milestone 4: Test laboratory

- Private test RKP support for controlled environments.
- CBOR and COSE debugging tools.
- Test CA integration.
- Certificate lifecycle visualization.

### Milestone 5: Long-term compatibility

- Android version and Mainline migration support.
- OEM compatibility database.
- Legacy keybox deprecation guidance when appropriate.
- Automated compatibility test coverage across multiple security levels.

## Non-goals

The following should remain outside the normal CleveresTricky roadmap:

- Extracting TEE, StrongBox, DICE, UDS, CDI, or RKP private secrets.
- Replacing Google's production RKP provisioning authority.
- Manufacturing Google-trusted RKP credentials.
- Treating a local test CA as a production Android trust root.
- Intercepting protected RKPD callers for certificate substitution.
- Claiming that a local certificate result guarantees a remote Play Integrity verdict.
- Hard-coding one OEM's secure-world implementation as if it were an Android standard.

## Closing direction

The long-term value of CleveresTricky should not depend on one certificate format or one generation of Android attestation.

Legacy keyboxes, KeyMint, StrongBox, RKP, DICE, Mainline provisioning, and future Android security services are parts of an evolving trust architecture. The project should understand those layers, preserve genuine platform behavior where required, and provide enough diagnostics to explain what the device is actually doing.

The best future compatibility layer is one that can adapt to a new Android security model without pretending the old one never changed.

[Return to the project overview](../README.md)
