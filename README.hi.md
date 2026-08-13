# CleveresTricky

**भाषा:** [English](README.md) | [Türkçe](README.tr.md) | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | **हिन्दी** | [العربية](README.ar.md)

[![Release](https://img.shields.io/github/v/release/tryigit/CleveresTricky?display_name=tag&sort=semver&label=Release)](https://github.com/tryigit/CleveresTricky/releases/latest)
![Android](https://img.shields.io/badge/Android-12--17-3DDC84?logo=android&logoColor=white)
![Module](https://img.shields.io/badge/Module-KernelSU%20%7C%20APatch-6f42c1)
![Architecture](https://img.shields.io/badge/Arch-ARM64%20%7C%20x86--64-0969DA)

CleveresTricky Android Keystore, attestation, identity और application compatibility के लिए KernelSU और APatch module है। यह controlled native runtime को mobile WebUI के साथ जोड़ता है ताकि application scope, key material, identity, patch level, Remote Key Provisioning protection और DRM compatibility एक ही जगह से प्रबंधित की जा सके।

> यह user documentation का localized version है। किसी technical अंतर की स्थिति में English documentation canonical source मानी जाएगी।

## मुख्य क्षमताएँ

### Runtime control

[Spoof Engine](docs/i18n/hi.md#spoof-engine) optional identity replacement को नियंत्रित करता है। Module service स्वस्थ रहने पर core Keystore, TEE compatibility और boot property protection इससे स्वतंत्र रूप से सक्रिय रहते हैं।

[Application Scope](docs/i18n/hi.md#application-scope) targeted mode, global mode, package rules, shared Android UID और live cache updates समझाता है।

[Application Rules](docs/i18n/hi.md#application-rules) app-specific template, keybox selection और stable privacy identity समझाता है।

[Profiles](docs/i18n/hi.md#profiles) Daily Compatibility, Default, Maximum Compatibility और Minimal presets समझाता है।

### Attestation और identity

[Attestation](docs/i18n/hi.md#attestation) certificate chain replacement, genuine KeyMint operations, StrongBox और software compatibility की सीमाएँ बताता है।

[Certificate Safe Mode](docs/i18n/hi.md#certificate-safe-mode) legacy configuration concept बताता है। वर्तमान core targeting अब उस पुराने switch पर निर्भर नहीं है।

[Keybox Manager](docs/i18n/hi.md#keybox-manager) keybox loading, verification, selection, rotation, revocation check और monitoring को कवर करता है।

[Automatic Keybox Check](docs/i18n/hi.md#automatic-keybox-check) bounded maintenance worker और उसके lifecycle को समझाता है।

[Remote Sources](docs/i18n/hi.md#remote-sources) authenticated retrieval, signature verification, refresh policy और failure behavior समझाता है।

[Encrypted Storage](docs/i18n/hi.md#encrypted-storage) CBOX containers, local protected caches और सुरक्षित key material handling समझाता है।

[Patch Levels](docs/i18n/hi.md#patch-levels) System, Vendor और Boot patch fields तथा global/per-app rules समझाता है।

[Build Identity](docs/i18n/hi.md#build-identity) device templates, fingerprint, app-visible Build fields, synchronized early-boot activation और Custom ROM users के लिए Pixel beta Auto Identity helper समझाता है।

[Identity Refresh](docs/i18n/hi.md#identity-refresh) अगले boot के लिए identity generation और snapshot consistency समझाता है।

[Telephony Identity](docs/i18n/hi.md#telephony-identity) dual SIM values, Android permission decision preservation, supported APIs और operator limits समझाता है।

### Platform compatibility

[Boot Properties](docs/i18n/hi.md#boot-properties) core userspace boot property view और अलग identity compatibility policy समझाता है।

[Region Properties](docs/i18n/hi.md#region-properties) optional bounded country/hardware region view समझाता है।

[Provider Coexistence](docs/i18n/hi.md#provider-coexistence) बताता है कि automatic mode किसी अन्य fingerprint provider को overwrite करने से कैसे बचता है।

[RKP Protection](docs/i18n/hi.md#rkp-protection) protected Android infrastructure और genuine generated-key passthrough समझाता है।

[DRM Passthrough and Privacy](docs/i18n/hi.md#drm-passthrough) दो अलग behaviors समझाता है। Selected media apps Android के genuine Keystore certificate path पर रह सकती हैं, जबकि `privacy=isolate` supported modern DRM HAL `deviceUniqueId` को app-specific stable pseudonym से बदल सकता है।

यह Widevine या DRM bypass नहीं है। Security level, licenses, provisioning, content keys, sessions, HDCP और string properties नहीं बदले जाते।

### Interface और operation

[Web Interface](docs/i18n/hi.md#web-interface) native module-manager transport, mobile navigation, live status, validation और accessibility समझाता है।

Built-in WebUI languages हैं **English**, **Türkçe**, **简体中文**, **Español**, **Deutsch**, **Русский**, **Bahasa Indonesia**, **हिन्दी** और **العربية**। Translation catalogs local हैं, इसलिए language switch के लिए network की जरूरत नहीं है।

[Backup and Restore](docs/i18n/hi.md#backup-restore) encrypted export, bounded import और safe recovery समझाता है।

[Installer](docs/i18n/hi.md#installer) KernelSU/APatch package layout, payload verification, supported devices और installation flow समझाता है।

[Diagnostics](docs/i18n/hi.md#diagnostics) logs, status checks, common failures और controlled troubleshooting sequence समझाता है।

### Engineering references

[Security Model](docs/i18n/hi.md#security-model) trust boundaries, protected files, input validation और वे capabilities दस्तावेज करता है जिनका module दावा नहीं करता।

[Performance](docs/i18n/hi.md#performance) hook lifecycle, bounded caches, background work, CPU और memory behavior दस्तावेज करता है।

[Building](docs/i18n/hi.md#building) toolchain, validation tasks और generated artifacts समझाता है।

[Native Architecture](docs/i18n/hi.md#native-architecture) Rust injector, Rust native core, enforced language policy और केवल आवश्यक Android C++ ABI boundary दस्तावेज करता है।

## Quick start

1. Official project Release page से current ZIP डाउनलोड करें।
2. Official build origin verify करना हो तो `SHA256SUMS` और GitHub build provenance जांचें।
3. Android चलते समय KernelSU या APatch खोलें।
4. ZIP install करके reboot करें।
5. Module manager से CleveresTricky WebUI खोलें।
6. Fresh install में Global Mode enabled और optional identity spoofing disabled रहता है।
7. केवल अपना या authorized test key material जोड़ें।
8. Identity options केवल जरूरत पर configure करें।
9. Template Build Identity बदलने के बाद reboot करें।

Project या release में usable keybox या private attestation key शामिल नहीं है।

## Supported environment

CleveresTricky Android 12 से Android 17, API 31 से 37, ARM64 और x86 64 को support करता है। Installation Android running state में KernelSU या APatch से की जाती है।

Magisk और recovery install supported नहीं हैं। Installer unsupported path को partial module छोड़ने से पहले रोक देता है।

## महत्वपूर्ण सीमाएँ

Result वास्तविक device state, firmware, certification, key material, Google Play services और remote policy पर निर्भर करता है। CleveresTricky local compatibility path सुधारता है लेकिन किसी specific remote verdict की guarantee नहीं देता।

Telephony values केवल supported app APIs में दिखाई देते हैं। Modem, baseband, EFS, physical SIM या operator को दिखने वाली identity नहीं बदलती।

Modern Android में Android ID, SettingsProvider के अंदर app signing identity, user और device से scoped है। CleveresTricky misleading global Android ID control नहीं देता।

Actual kernel version नहीं बदलता। Boot property view bootloader को physically relock नहीं करता, verified boot repair नहीं करता, vbmeta rewrite नहीं करता और hardware root of trust नहीं बदलता।

Unlocked bootloader का मतलब यह नहीं कि हर DRM implementation unusable है। व्यवहार device, vendor implementation, provisioning, security level, service policy और firmware पर निर्भर है। Current DRM feature privacy-focused है, bypass नहीं।

Internal SHA 256 records missing, changed, injected, linked या unexpected payload detect करते हैं और validation failure पर tamper lockdown सक्रिय करते हैं। Official release authenticity अलग published digest और GitHub signed build provenance से आती है।

## Recommended first setup

पहले fresh-install defaults उपयोग करें। Global Mode eligible app UIDs चुनता है जबकि core boot और Keystore protection सक्रिय रहते हैं। Optional identity spoofing Identity section से enable करने तक बंद रहता है।

Custom ROM पर Play Integrity testing के लिए current Build Identity चाहिए तो Auto Identity Google public metadata से Pixel beta या canary identity प्राप्त कर local store कर सकता है। केवल तब Identity Spoof Engine enable करके reboot करें जब इन values को expose करना हो।

DRM identifier privacy के लिए media app पर Application Rule बनाएं और `privacy=isolate` सेट करें। DRM Keystore Passthrough enabled रह सकता है क्योंकि genuine Keystore certificate path और pseudonymous DRM ID path स्वतंत्र हैं।

## सहायता और project information

Troubleshooting के लिए WebUI Logs या `CleveresTricky` tag वाला Android logcat उपयोग करें। Details [Diagnostics](docs/i18n/hi.md#diagnostics) में हैं।

Project history [CHANGELOG](docs/i18n/hi.md#changelog), contribution guide [Contributing](docs/i18n/hi.md#contributing), language structure [Language Support](docs/i18n/hi.md#languages), और theme [Theme](docs/i18n/hi.md#theme) में है।

Official [Android attestation documentation](https://source.android.com/docs/security/features/keystore/attestation), [Play Integrity verdict documentation](https://developer.android.com/google/play/integrity/verdicts) और [KernelSU module guide](https://kernelsu.org/guide/module.html) संबंधित platforms के authoritative sources हैं।

## Granular optional identity controls

CleveresTricky Device and Build Identity, Attestation Identity, Telephony Identity, Region Identity, Identity Refresh और Security Patch को स्वतंत्र रूप से resolve करता है। Security Patch, Device and Build Identity से independent है।

Core Keystore interception, genuine KeyMint/StrongBox private-key operations, root-of-trust handling, Binder safety और required boot compatibility इन optional controls से स्वतंत्र रहते हैं। Interface captured real state, configured presentation state और app-visible effective state अलग दिखाता है।

Security Patch में System, Vendor और Boot के लिए independent policies हैं। Profiles applications को coherent optional settings assign कर सकते हैं और Effective State inspector runtime resolver का अंतिम परिणाम दिखाता है।
