# CleveresTricky

[![Build](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml/badge.svg)](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml)

### Advanced Spoofing Module for Android

---

## Minimum Requirements

| Requirement | Details |
|-------------|---------|
| **Root Manager** | Magisk v26.0+ (with Zygisk enabled) or KernelSU v0.7.0+ (with Zygisk Next) |
| **Android API** | Minimum SDK 31 (Android 12) |
| **Architecture** | arm64-v8a, armeabi-v7a, x86_64, x86 |
| **Platform** | Qualcomm (Snapdragon) or MediaTek (Dimensity/Helio) for TEE-level features |
| **SELinux** | Enforcing (module handles policy via sepolicy.rule) |
| **Zygisk** | Required (Magisk native Zygisk or Zygisk Next for KernelSU) |

> **Note:** IMEI provisioning and TEE attestation features require Qualcomm (`/dev/qseecom`) or MediaTek (`/dev/tee0`) hardware. Other SoCs can use all non-TEE features (property spoofing, keybox management, etc.).

---

## Key Features

### Keystore & Attestation
| Feature | Description |
|---------|-------------|
| **Binder-level property spoofing** | Intercepts `__system_property_get` at native level, invisible to DroidGuard |
| **KeyMint 4.0 support** | Full compatibility with modern hardware attestation |
| **RKP Emulation** | Local proxy generates RFC-compliant COSE/CBOR proofs for STRONG integrity |
| **Keybox Jukebox** | Multi-key rotation with automatic round-robin selection |
| **Encrypted Keyboxes (.cbox)** | AES-256-GCM encrypted containers with hardware-backed caching |
| **Remote Key Servers** | Auto-fetch and rotate keyboxes from community servers |
| **Auto Revocation Check** | Checks keys against Google's CRL every 24h |

### Privacy & Identity
| Feature | Description |
|---------|-------------|
| **IMEI/Serial Changer** | System-wide IMEI, IMSI, ICCID, Serial spoofing via Binder (Qualcomm/MediaTek) |
| **Randomize on Boot** | Fresh device identity (template + IMEI + Serial + MAC) every reboot |
| **Dynamic Identity Mutation** | Anti-fingerprinting -- rotates root secrets every 24h |
| **DRM ID Regeneration** | Reset Widevine device ID to bypass download limits |
| **Location Spoofing** | Simulate GPS coordinates with optional random location mode (auto-drift within radius) |
| **MAC Address Spoofing** | WiFi and Bluetooth MAC address randomization |
| **One-Click Reset** | Instantly regenerate all identities and refresh the environment |

### Spoof Modes
| Mode | Description |
|------|-------------|
| **Target Only** (default) | Only apps listed in `target.txt` are affected by spoofing |
| **Global Mode** | All apps are spoofed; `target.txt` becomes an exclusion list |
| **IMEI Global** | IMEI/modem spoofing applies to all apps, independent of Global Mode |
| **Network Global** | WiFi/BT MAC spoofing applies to all apps, independent of Global Mode |

> Most features only affect apps in `target.txt` by default. Advanced features like IMEI changing have their own per-feature global toggles.

### System Integration
| Feature | Description |
|---------|-------------|
| **Device Templates** | Built-in profiles: `pixel8pro`, `pixel7pro`, `xiaomi14`, `s23ultra`, `oneplus11`, etc. |
| **Per-App Spoofing** | Assign specific templates and keyboxes per application |
| **AutoPIF** | Fetches latest Pixel Beta/Canary fingerprints from Google servers |
| **WebUI Dashboard** | Full configuration, backup/restore, keybox management via browser |
| **Security Patch Sync** | Customizable per-component patch levels with dynamic date support |
| **DRM / Netflix Fix** | Widevine L1 spoof, encrypted state, streaming compatibility |

### Platform Support (Qualcomm / MediaTek)
| Feature | Qualcomm | MediaTek |
|---------|----------|----------|
| **TEE ID Attestation Provisioning** | `/dev/qseecom` or `/dev/smd` | `/dev/tee0` |
| **IMEI Provisioning** | `provision_device_ids` | `provision_device_ids_mtk` |
| **Hardware Keystore** | Supported | Supported |
| **Binder Interception** | arm64/x86_64 | arm64 |

> Custom IMEI, Serial, and hardware identity provisioning is available for Qualcomm Snapdragon and MediaTek Dimensity/Helio chipsets. The module auto-detects the platform and uses the appropriate provisioning binary.

### Architecture
| Component | Technology |
|-----------|-----------|
| **Native Layer** | Rust FFI + C++ Binder interceptor (zero-copy, panic-safe) |
| **Stealth Daemon** | `kworker` disguised process with anti-ptrace, memory sanitization |
| **Service Layer** | Kotlin coroutine-based with FileObserver hot-reload |
| **Build System** | Gradle + cargo-ndk + CMake (arm64, arm, x86, x86_64) |
| **CI Pipeline** | Safety gate -> Rust tests -> Instrumentation tests -> Build & release |

### Integrity Levels
- **MEETS_USE_BRAIN**

---

## Quick Start

1. **Install** Flash the module ZIP from Magisk manager and reboot
2. **Configure** Open WebUI at `http://localhost:5623` (port shown in module logs)
3. **Add Keybox** *(optional)* Place `keybox.xml` at `/data/adb/cleverestricky/keybox.xml`
4. **Enable RKP** *(for STRONG integrity)* `touch /data/adb/cleverestricky/rkp_bypass`
5. **Set Targets** *(optional)* Add package names to `/data/adb/cleverestricky/target.txt`

> **Tip:** Configuration changes take effect immediately, no reboot needed.

---

## Documentation

| Document | Description |
|----------|-------------|
| [LOG.md](LOG.md) | Logcat filters and debugging guide |
| [DETECTION_ANALYSIS.md](DETECTION_ANALYSIS.md) | Security analysis: eBPF, DroidGuard, timing checks |
| [FFI_SAFETY_NOTES.md](FFI_SAFETY_NOTES.md) | Rust FFI safety audit and memory ownership model |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to contribute to the project |
| [LANGUAGES.md](LANGUAGES.md) | WebUI translation guide |
| [THEME.md](THEME.md) | WebUI theme customization |
| [DONATE.md](DONATE.md) | Support development |

---

## Configuration Reference

### Keybox (keybox.xml)

```xml
<?xml version="1.0"?>
<AndroidAttestation>
    <NumberOfKeyboxes>1</NumberOfKeyboxes>
    <Keybox DeviceID="...">
        <Key algorithm="ecdsa|rsa">
            <PrivateKey format="pem">
-----BEGIN EC PRIVATE KEY-----
...
-----END EC PRIVATE KEY-----
            </PrivateKey>
            <CertificateChain>
                <NumberOfCertificates>...</NumberOfCertificates>
                <Certificate format="pem">
-----BEGIN CERTIFICATE-----
...
-----END CERTIFICATE-----
                </Certificate>
            </CertificateChain>
        </Key>
    </Keybox>
</AndroidAttestation>
```

### Build Vars (`spoof_build_vars`)

> Requires Zygisk or Zygisk Next

```ini
MANUFACTURER=Google
MODEL=Pixel 8 Pro
FINGERPRINT=google/husky/husky:15/AP4A.250305.002/12737840:user/release-keys
BRAND=google
PRODUCT=husky
DEVICE=husky
RELEASE=15
ID=AP4A.250305.002
SECURITY_PATCH=2025-03-05
# Use a built-in template instead of manual values:
# TEMPLATE=pixel8pro

# Location Spoofing (requires spoof_location toggle)
# SPOOF_LATITUDE=41.0082
# SPOOF_LONGITUDE=28.9784
# SPOOF_ALTITUDE=0
# SPOOF_ACCURACY=1.0
# SPOOF_LOCATION_RANDOM=false
# SPOOF_LOCATION_RADIUS=500
# SPOOF_LOCATION_INTERVAL=30
```

<details>
<summary><b>All Supported Keys</b></summary>

| Key | Example |
|-----|---------|
| `MANUFACTURER` | `Google` |
| `MODEL` | `Pixel 8 Pro` |
| `FINGERPRINT` | `google/husky/husky:15/...` |
| `BRAND` | `google` |
| `PRODUCT` | `husky` |
| `DEVICE` | `husky` |
| `RELEASE` | `15` |
| `ID` | `AP4A.250305.002` |
| `DISPLAY` | `AP4A.250305.002` |
| `INCREMENTAL` | `12737840` |
| `TYPE` | `user` |
| `TAGS` | `release-keys` |
| `SECURITY_PATCH` | `2025-03-05` |
| `BOOTLOADER` | `...` |
| `BOARD` | `...` |
| `HARDWARE` | `...` |
| `SDK_INT` | `35` |
| `CODENAME` | `REL` |
| `TEMPLATE` | `pixel8pro` |
| `ATTESTATION_ID_BRAND` | `google` |
| `ATTESTATION_ID_DEVICE` | `husky` |
| `ATTESTATION_ID_MODEL` | `Pixel 8 Pro` |
| `ATTESTATION_ID_IMEI` | `35...` (Luhn-valid 15 digits) |
| `ATTESTATION_ID_SERIAL` | `ABC123...` |
| `ATTESTATION_ID_WIFI_MAC` | `00:11:22:33:44:55` |
| `ATTESTATION_ID_BT_MAC` | `00:11:22:33:44:55` |
| `SPOOF_LATITUDE` | `41.0082` |
| `SPOOF_LONGITUDE` | `28.9784` |
| `SPOOF_ALTITUDE` | `0` |
| `SPOOF_ACCURACY` | `1.0` |
| `SPOOF_LOCATION_RANDOM` | `true` / `false` |
| `SPOOF_LOCATION_RADIUS` | `500` (meters) |
| `SPOOF_LOCATION_INTERVAL` | `30` (seconds) |

</details>

### Target Packages (`target.txt`)

```bash
# Standard mode (leaf certificate replacement)
io.github.vvb2060.keyattestation

# Generate mode for TEE-broken devices (append !)
com.google.android.gms!
```

### Per-App Configuration (`app_config`)

```bash
# Format: packageName [template] [keybox_filename] [permissions]
com.google.android.gms pixel8pro keybox_beta.xml
com.netflix.mediaclient xiaomi14 null
com.google.android.apps.walletnfcrel null keybox_wallet.xml
```

### Security Patch (`security_patch.txt`)

```ini
# Simple (applied to all components)
20250305

# Advanced (per-component)
system=202503
boot=2025-03-05
vendor=2025-03-05

# Dynamic (auto-updates)
today
```

---

## Advanced

### RKP Spoofing (STRONG Integrity)

```bash
# Enable
touch /data/adb/cleverestricky/rkp_bypass

# Disable
rm /data/adb/cleverestricky/rkp_bypass
```

The module uses a **Local RKP Proxy** that generates valid COSE/CBOR structures signed by a rotating root secret. The identity mutates every 24 hours to avoid fingerprint banning.

### Custom RKP Keys (`remote_keys.xml`)

```xml
<RemoteKeyProvisioning>
    <Keys>
        <Key>
            <PrivateKey format="pem">
-----BEGIN EC PRIVATE KEY-----
...
-----END EC PRIVATE KEY-----
            </PrivateKey>
        </Key>
    </Keys>
    <HardwareInfo>
        <RpcAuthorName>Google</RpcAuthorName>
        <VersionNumber>3</VersionNumber>
    </HardwareInfo>
</RemoteKeyProvisioning>
```

### AutoPIF (Fingerprint Updates)

```bash
# Manual execution
sh /data/adb/modules/cleverestricky/autopif.sh

# Specific device
sh /data/adb/modules/cleverestricky/autopif.sh --device husky

# Enable automatic (24h interval)
touch /data/adb/cleverestricky/auto_beta_fetch
```

### DRM & Streaming Fix

Enable via WebUI or shell:
```bash
touch /data/adb/cleverestricky/drm_fix
```

Overrides: `ro.netflix.bsp_rev=0`, `drm.service.enabled=true`, `ro.com.google.widevine.level=1`, `ro.crypto.state=encrypted`

### Randomize on Boot

```bash
touch /data/adb/cleverestricky/random_on_boot
```

Generates fresh IMEI (Luhn-compliant), Serial, MAC addresses, and selects a random device template on every boot.

### Location Spoofing

Enable via WebUI toggle or shell:
```bash
touch /data/adb/cleverestricky/spoof_location
```

Then set coordinates in `spoof_build_vars`:
```ini
SPOOF_LATITUDE=41.0082
SPOOF_LONGITUDE=28.9784
SPOOF_ALTITUDE=0
SPOOF_ACCURACY=1.0
```

Location spoofing simulates GPS coordinates for target apps. Qualcomm and MediaTek devices are supported.

### Per-Feature Global Modes

```bash
# IMEI/modem spoofing for ALL apps (not just target.txt)
touch /data/adb/cleverestricky/imei_global

# Network/MAC spoofing for ALL apps
touch /data/adb/cleverestricky/network_global
```

These toggles allow specific advanced features to apply system-wide without enabling full Global Mode.

---

## Roadmap

- [ ] Zygisk-less standalone mode
- [ ] Enhanced KernelSU native integration
- [ ] Advanced detection evasion independent of Zygisk injection
- [ ] Contact information spoofing
- [ ] Full device state backup and restore

## Acknowledgements

- [PlayIntegrityFix](https://github.com/chiteroman/PlayIntegrityFix) - Original inspiration
- [FrameworkPatch](https://github.com/chiteroman/FrameworkPatch) - Framework patching
- [BootloaderSpoofer](https://github.com/chiteroman/BootloaderSpoofer) - Bootloader state spoofing
- [KeystoreInjection](https://github.com/aviraxp/Zygisk-KeystoreInjection) - Zygisk-based injection
- [LSPosed](https://github.com/LSPosed/LSPosed) - Xposed framework

## Community

**Telegram:** [Cleverestech Group](https://t.me/cleverestech)

## Support

If you find this project useful, consider supporting its development: [DONATE.md](DONATE.md)
