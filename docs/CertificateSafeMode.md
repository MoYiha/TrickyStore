# Certificate Safe Mode

## Purpose

Certificate Safe Mode keeps genuine Android certificate responses while leaving the CleveresTricky service and diagnostic interface available. It is useful when isolating a compatibility problem without uninstalling the module.

## Runtime behavior

The keystore control path remains observable, but certificate substitution is skipped. Key creation, KeyMint operations, generated provisioning responses, and application error behavior remain on the platform path.

The master Spoof Engine control still has priority. Turning the engine off parks native hooks and stops active interception work. Certificate Safe Mode is narrower because it preserves the running service and other explicitly enabled compatibility controls.

## When to use it

Enable this mode when testing whether a certificate chain change caused an application regression. Restart the application after changing the mode. If the application captured identity or property values during startup, reboot before drawing a conclusion.

Safe Mode does not repair a broken hardware trust path and does not modify the TEE. It simply prevents CleveresTricky from replacing supported certificate responses.

[Return to the project overview](../README.md)
