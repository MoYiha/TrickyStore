# CleveresTricky

**भाषा:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | **हिन्दी** | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)

CleveresTricky Android 12–17 के लिए KernelSU और APatch मॉड्यूल है। यह Android Keystore और attestation compatibility, Keybox/CBOX management, app targeting, optional identity controls, patch-level controls और privacy tools को एक mobile WebUI में लाता है।

पहले default settings से शुरू करें और केवल वही features चालू करें जिनकी आपको वास्तव में ज़रूरत है।

## आप क्या कर सकते हैं

- **Keybox/CBOX** files को manage, verify, select और rotate करें।
- Global Mode इस्तेमाल करें या अलग-अलग apps पर per-app rules लागू करें।
- Device/build, attestation, telephony, region और security patch presentation को आवश्यकता के अनुसार configure करें।
- Remote Key Provisioning flows को protect करें और DRM bypass का दावा किए बिना supported DRM identifiers की exposure कम करें।
- Settings का backup लें, effective state देखें और WebUI या module Action से diagnostics इकट्ठा करें।

## Quick start

1. Official [Releases](https://github.com/tryigit/CleveresTricky/releases/latest) page से latest ZIP डाउनलोड करें।
2. Android चल रहा हो तब ZIP को KernelSU या APatch से install करें।
3. अपने module manager से CleveresTricky WebUI खोलें।
4. केवल वही **Keybox या CBOX** जोड़ें जो आपका हो या जिसे test करने की आपको अनुमति हो।
5. पहले default configuration रखें; identity, app rules या privacy options केवल ज़रूरत होने पर enable करें।

Project के साथ कोई usable Keybox या private attestation key शामिल नहीं है।

## Supported environment

- Android **12–17** / API **31–37**
- **ARM64** और **x86-64**
- **KernelSU** और **APatch**

Magisk और recovery installation supported नहीं हैं।

## ज़रूरी बातें

CleveresTricky local compatibility path को बेहतर करता है, लेकिन remote result असली device, firmware, certification state, Google Play services, server policy और आपकी configuration पर निर्भर रहता है। यह किसी खास Play Integrity या attestation verdict की guarantee नहीं दे सकता।

यह bootloader को physically relock नहीं करता, Verified Boot measurements को rewrite नहीं करता, hardware Root of Trust को नहीं बदलता, modem/baseband को modify नहीं करता और DRM privacy controls को DRM bypass में नहीं बदलता।

केवल वही configuration और credentials इस्तेमाल करें जिन्हें उपयोग करने की आपको अनुमति हो।

## और जानकारी

- [Keybox Manager](docs/KeyboxManager.md) — Keybox/CBOX loading, verification, selection और revocation checks।
- [Application Scope](docs/ApplicationScope.md) और [Application Rules](docs/ApplicationRules.md) — तय करें features किन apps पर लागू हों।
- [Build Identity](docs/BuildIdentity.md), [Telephony Identity](docs/TelephonyIdentity.md) और [Patch Levels](docs/PatchLevels.md) — optional identity controls।
- [RKP Protection](docs/RkpProtection.md) और [DRM Privacy](docs/DrmPassthrough.md) — platform compatibility और privacy behavior।
- [Backup and Restore](docs/BackupRestore.md) — encrypted configuration backup और recovery।
- [Security Model](docs/SecurityModel.md) और [Installer](docs/Installer.md) — trust boundaries और installation details।

## मदद चाहिए?

WebUI के **Logs** page या module **Action** से emergency diagnostic report बनाएं। Share करने से पहले archive को देख लें, क्योंकि diagnostics में device और system information हो सकती है।

Common problems और troubleshooting steps के लिए [Diagnostics](docs/Diagnostics.md) देखें।

## Project

[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Languages](LANGUAGES.md) · [Licensing](LICENSING.md) · [Telegram](https://t.me/cleverestech)
