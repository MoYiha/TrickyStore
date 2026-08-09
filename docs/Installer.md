# Installer

## Purpose

The installer creates a complete KernelSU or APatch module with the service, native payload, scripts, policy, metadata, and integrity records required at runtime.

## Supported path

Installation must run from KernelSU or APatch while Android is active. Android 12 through Android 16 are supported on ARM64 and x86 64. Recovery and Magisk paths stop with an explanation before a partial module is left behind.

The installer selects the architecture specific Rust `inject` executable and `libcleverestricky.so` library. It also installs the daemon, service APK, module metadata, installer script, early boot script, service script, action script, and SELinux policy required by their lifecycle stage.

Binary and script payloads are intentional parts of the module template. KernelSU and APatch use the metadata and boot scripts to recognize and start the module. The build process verifies that required files and both native architecture outputs exist before creating a ZIP.

## Integrity and permissions

The build adds a SHA 256 record for each packaged payload. Installation verifies extracted content, sets executable modes only where required, creates the private configuration directory with root ownership, and refuses a symbolic link in that location.

Runtime scripts restore conservative file modes and SELinux labels without recursively following unexpected links or crossing mounts.

## Installation sequence

1. Download the release ZIP for the project.

2. Install it through KernelSU or APatch.

3. Confirm that the manager reports success.

4. Reboot Android.

5. Open the module Action screen and review Dashboard and Logs.

Do not extract or delete template binaries manually. An incomplete payload will fail verification or prevent native runtime activation.

[Return to the project overview](../README.md)
