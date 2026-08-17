# CleveresTricky

**Language:** **English** | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)

CleveresTricky is a KernelSU and APatch module for Android 12–17. It brings Android keystore and attestation compatibility, Keybox/CBOX management, application targeting, optional identity controls, patch-level controls, and privacy tools into one mobile WebUI.

Start with the defaults and enable only the features you actually need.

## What you can do

- Manage, verify, select, and rotate **Keybox/CBOX** files.
- Use Global Mode or target individual applications with per-app rules.
- Configure optional device/build, attestation, telephony, region, and security patch presentation.
- Protect Remote Key Provisioning flows and reduce supported DRM identifier exposure without pretending to bypass DRM.
- Back up settings, inspect effective state, and collect diagnostics from the WebUI or module Action.

## Quick start

1. Download the latest release ZIP from the official [Releases](https://github.com/tryigit/CleveresTricky/releases/latest) page.
2. Install the ZIP from KernelSU or APatch while Android is running.
3. Open the CleveresTricky WebUI from your module manager.
4. Add only a **Keybox or CBOX** that you own or are authorized to test.
5. Keep the default setup first, then enable identity, application rules, or privacy options only when needed.

No usable Keybox or private attestation key is bundled with the project.

## Supported environment

- Android **12–17** / API **31–37**
- **ARM64** and **x86-64**
- **KernelSU** and **APatch**

Magisk and recovery installation are not supported.

## Important to know

CleveresTricky improves the local compatibility path, but remote results still depend on the real device, firmware, certification state, Google Play services, server policy, and the data you configure. It cannot guarantee a particular Play Integrity or attestation verdict.

It does not physically relock the bootloader, rewrite verified boot measurements, change the hardware root of trust, modify the modem/baseband, or turn DRM privacy controls into a DRM bypass.

Use only configuration and credentials that you are authorized to use.

## Learn more

- [Keybox Manager](docs/KeyboxManager.md) — Keybox/CBOX loading, verification, selection, and revocation checks.
- [Application Scope](docs/ApplicationScope.md) and [Application Rules](docs/ApplicationRules.md) — choose where features apply.
- [Build Identity](docs/BuildIdentity.md), [Telephony Identity](docs/TelephonyIdentity.md), and [Patch Levels](docs/PatchLevels.md) — optional identity controls.
- [RKP Protection](docs/RkpProtection.md) and [DRM Privacy](docs/DrmPassthrough.md) — platform compatibility and privacy behavior.
- [Backup and Restore](docs/BackupRestore.md) — encrypted configuration backup and recovery.
- [Security Model](docs/SecurityModel.md) and [Installer](docs/Installer.md) — trust boundaries and installation details.

## Need help?

Use the **Logs** page in the WebUI or the module **Action** to create an emergency diagnostic report. Review the archive before sharing it because diagnostics can contain device and system information.

See [Diagnostics](docs/Diagnostics.md) for common problems and troubleshooting steps.

## Project

[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Languages](LANGUAGES.md) · [Licensing](LICENSING.md) · [Telegram](https://t.me/cleverestech)
