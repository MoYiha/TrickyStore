# CleveresTricky

[![Build](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml/badge.svg)](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml)

### Advanced Spoofing Module for Android

---

## ✨ Key Features

### 🔐 Keystore & Attestation
| Feature | Description |
|---------|-------------|
| **Binder-level property spoofing** | Intercepts `__system_property_get` at native level, invisible to DroidGuard |
| **KeyMint 4.0 support** | Full compatibility with modern hardware attestation |
| **RKP Emulation** | Local proxy generates RFC-compliant COSE/CBOR proofs for STRONG integrity |
| **Keybox Jukebox** | Multi-key rotation with automatic round-robin selection |
| **Encrypted Keyboxes (.cbox)** | AES-256-GCM encrypted containers with hardware-backed caching |
| **Remote Key Servers** | Auto-fetch and rotate keyboxes from community servers |
| **Auto Revocation Check** | Checks keys against Google's CRL every 24h |

### 🛡️ Privacy & Identity
| Feature | Description |
|---------|-------------|
| **IMEI/Serial Changer** | System-wide IMEI, IMSI, ICCID, Serial spoofing via Binder |
| **Randomize on Boot** | Fresh device identity (template + IMEI + Serial + MAC) every reboot |
| **Dynamic Identity Mutation** | Anti-fingerprinting — rotates root secrets every 24h |
| **DRM ID Regeneration** | Reset Widevine device ID to bypass download limits |

### ⚙️ System Integration
| Feature | Description |
|---------|-------------|
| **Device Templates** | Built-in profiles: `pixel8pro`, `pixel7pro`, `xiaomi14`, `s23ultra`, `oneplus11`, etc. |
| **Per-App Spoofing** | Assign specific templates and keyboxes per application |
| **AutoPIF** | Fetches latest Pixel Beta/Canary fingerprints from Google servers |
| **WebUI Dashboard** | Full configuration, backup/restore, keybox management via browser |
| **Security Patch Sync** | Customizable per-component patch levels with dynamic date support |
| **DRM / Netflix Fix** | Widevine L1 spoof, encrypted state, streaming compatibility |

### 🏗️ Architecture
| Component | Technology |
|-----------|-----------|
| **Native Layer** | Rust FFI + C++ Binder interceptor (zero-copy, panic-safe) |
| **Stealth Daemon** | `kworker` disguised process with anti-ptrace, memory sanitization |
| **Service Layer** | Kotlin coroutine-based with FileObserver hot-reload |
| **Build System** | Gradle + cargo-ndk + CMake (arm64, arm, x86, x86_64) |
| **CI Pipeline** | Safety gate → Rust tests → Instrumentation tests → Build & release |

### ✅ Integrity Levels
- **MEETS_USE_BRAIN** ✅

---

## 🚀 Quick Start

1. **Install** Flash the module ZIP from Magisk/KernelSU manager and reboot
2. **Configure** Open WebUI at `http://localhost:5623` (port shown in module logs)
3. **Add Keybox** *(optional)* Place `keybox.xml` at `/data/adb/cleverestricky/keybox.xml`
4. **Enable RKP** *(for STRONG integrity)* `touch /data/adb/cleverestricky/rkp_bypass`
5. **Set Targets** *(optional)* Add package names to `/data/adb/cleverestricky/target.txt`

> **Tip:** Configuration changes take effect immediately, no reboot needed.

---

## 📖 Documentation

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

## 📋 Configuration Reference

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
FINGERPRINT=google/husky_beta/husky:15/AP31.240617.009/12094726:user/release-keys
BRAND=google
PRODUCT=husky_beta
DEVICE=husky
RELEASE=15
ID=AP31.240617.009
SECURITY_PATCH=2025-03-05
# Use a built-in template instead of manual values:
# TEMPLATE=pixel8pro
```

<details>
<summary><b>All Supported Keys</b></summary>

| Key | Example |
|-----|---------|
| `MANUFACTURER` | `Google` |
| `MODEL` | `Pixel 8 Pro` |
| `FINGERPRINT` | `google/husky/husky:15/...` |
| `BRAND` | `google` |
| `PRODUCT` | `husky_beta` |
| `DEVICE` | `husky` |
| `RELEASE` | `15` |
| `ID` | `AP31.240617.009` |
| `DISPLAY` | `AP31.240617.009` |
| `INCREMENTAL` | `12094726` |
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

## 🔧 Advanced

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

---

## 🗺️ Roadmap

- [ ] Zygisk-less standalone mode
- [ ] Enhanced KernelSU native integration
- [ ] Advanced detection evasion independent of Zygisk injection

## 🙏 Acknowledgements

- [PlayIntegrityFix](https://github.com/chiteroman/PlayIntegrityFix) - Original inspiration
- [FrameworkPatch](https://github.com/chiteroman/FrameworkPatch) - Framework patching
- [BootloaderSpoofer](https://github.com/chiteroman/BootloaderSpoofer) - Bootloader state spoofing
- [KeystoreInjection](https://github.com/aviraxp/Zygisk-KeystoreInjection) - Zygisk-based injection
- [LSPosed](https://github.com/LSPosed/LSPosed) - Xposed framework

## 💬 Community

**Telegram:** [Cleverestech Group](https://t.me/cleverestech)

## ❤️ Support

If you find this project useful, consider supporting its development: [DONATE.md](DONATE.md)
