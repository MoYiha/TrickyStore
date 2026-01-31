# CleveresTricky (Beta)

**The AI-Powered, Unrestricted, God-Tier Keystore & Attestation Spoofing Module**

*Formerly TrickyStore*

**Android 12 or above is required**.

## Why CleveresTricky?

Compared to the standard TrickyStore, **CleveresTricky** brings:
- **AI-Powered Continuous Updates:** Leveraging advanced AI to stay ahead of Google's detections. More security vulnerabilities are being fixed.
- **Unrivaled Security & Stealth:** Implements **Binder-level System Property Spoofing** to hide sensitive props (like `ro.boot.verifiedbootstate`) from deep inspection methods (DroidGuard/GMS) without relying on fragile hooking frameworks for every app.
- **Peak Performance:** Optimized C++ injection and lightweight Java service.
- **Low RAM Usage:** CleveresTricky is optimized for low RAM devices. It automatically releases memory used by configuration files (like `keybox.xml`) immediately after parsing.
- **God-Mode Features:**
    - **Safe Binder Spoofing:** Bypasses ABI issues to safely spoof system properties at the IPC level.
    - **KeyMint 4.0 Support:** Ready for the future.
    - **Module Hash Spoofing:** (Experimental) To match official firmware fingerprints. ... more

## Usage

1. Flash this module and reboot.  
2. For more than DEVICE integrity, put an unrevoked hardware keybox.xml at `/data/adb/cleverestricky/keybox.xml` (Optional).
3. Customize target packages at `/data/adb/cleverestricky/target.txt` (Optional).
4. Enjoy!  

**All configuration files will take effect immediately.**

## keybox.xml

format:

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
                ... more certificates
            </CertificateChain>
        </Key>...
    </Keybox>
</AndroidAttestation>
```

## Build Vars Spoofing (Advanced Privacy)

> **Zygisk (or Zygisk Next) is needed for this feature to work.**

CleveresTricky allows you to spoof ANY system property via Binder interception, making it invisible to standard `getprop` checks from targeted apps.

Create/edit `/data/adb/cleverestricky/spoof_build_vars`.

Example:

```
MANUFACTURER=Google
MODEL=Pixel 8 Pro
FINGERPRINT=google/husky_beta/husky:15/AP31.240617.009/12094726:user/release-keys
BRAND=google
PRODUCT=husky_beta
DEVICE=husky
RELEASE=15
ID=AP31.240617.009
INCREMENTAL=12094726
TYPE=user
TAGS=release-keys
SECURITY_PATCH=2024-07-05
# Advanced hidden props
ro.boot.verifiedbootstate=green
ro.boot.flash.locked=1
```

For Magisk users: if you don't need this feature and zygisk is disabled, please remove or rename the
folder `/data/adb/modules/cleveres_tricky/zygisk` manually.

## Device Templates

CleveresTricky includes built-in templates for popular devices to make spoofing easier. You can apply these templates via the Web UI or by adding `TEMPLATE=<name>` to your `spoof_build_vars` file.

**Available Templates:**
*   `pixel8pro` (Google Pixel 8 Pro)
*   `pixel7pro` (Google Pixel 7 Pro)
*   `pixel6pro` (Google Pixel 6 Pro)
*   `xiaomi14` (Xiaomi 14)
*   `s23ultra` (Samsung Galaxy S23 Ultra)
*   `oneplus11` (OnePlus 11)

**Usage via Web UI:**
1.  Open the Web UI.
2.  Select `spoof_build_vars` from the file selector.
3.  Choose a device from the "Select a device template..." dropdown.
4.  Click "Load Template".
5.  Click "Save File".

**Usage via File:**
Edit `/data/adb/cleverestricky/spoof_build_vars`:
```text
TEMPLATE=pixel7pro
# You can override specific variables if needed
MODEL=My Custom Model
```

## Support TEE broken devices

CleveresTricky will hack the leaf certificate by default. On TEE broken devices, this will not work because we can't retrieve the leaf certificate from TEE. You can add a `!` after a package name to enable generate certificate support for this package.

For example:

```
# target.txt
# use leaf certificate hacking mode for KeyAttestation App
io.github.vvb2060.keyattestation
# use certificate generating mode for gms
com.google.android.gms!
```

## Remote Key Provisioning (RKP) Spoofing

> **Android 12+ required for this feature. Achieves MEETS_STRONG_INTEGRITY!**

CleveresTricky can spoof the `IRemotelyProvisionedComponent` HAL to bypass Google's Remote Key Provisioning verification. This is the **key feature** for achieving **STRONG** integrity in Play Integrity checks.

### How to Enable

1. Create an empty file to enable RKP bypass:
   ```bash
   touch /data/adb/cleverestricky/rkp_bypass
   ```

2. Reboot or restart the keystore service.

### Optional: Custom Remote Keys

For advanced users, you can provide custom remote keys in `/data/adb/cleverestricky/remote_keys.xml`. See the template in the module for format details.

### Verification

After enabling RKP bypass, use the [Play Integrity API Checker](https://play.google.com/store/apps/details?id=gr.nickas.playintegrity) to verify you get:
- ✅ `MEETS_BASIC_INTEGRITY`
- ✅ `MEETS_DEVICE_INTEGRITY`
- ✅ `MEETS_STRONG_INTEGRITY`

## AutoPIF - Automatic Pixel Beta Fingerprints

> **Automatic fingerprint updates for STRONG integrity!**

CleveresTricky can automatically fetch the latest Pixel Beta/Canary fingerprints from Google's servers.

### Manual Run (Terminal/ADB)

```bash
# Fetch and apply latest fingerprint (random device)
sh /data/adb/modules/cleveres_tricky/autopif.sh

# Specify device
sh /data/adb/modules/cleveres_tricky/autopif.sh --device husky

# List available devices (JSON)
sh /data/adb/modules/cleveres_tricky/autopif.sh --list
```

### Web UI Integration

Click the "Fetch Beta" button in the CleveresTricky Web UI to trigger an update.

### Background Auto-Update

For automatic updates (battery-optimized, once per 24 hours):

```bash
# Enable background updates
touch /data/adb/cleverestricky/auto_beta_fetch
```

## Security Patch Customization

> **Since v1.2.1 - Full control over security patch levels**

Create `/data/adb/cleverestricky/security_patch.txt` to customize patch levels.

### Simple Format

```
# Hack all security patch levels
20241101
```

### Advanced Format

```
# System patch level (YYYYMM format for short)
system=202411

# Boot patch level (keep in sync with attestation)
boot=2024-11-01

# Vendor patch level
vendor=2024-11-01

# Or disable specific levels
# boot=no

# Keep system prop consistent
# system=prop
```

### TrickyStore Compatibility

CleveresTricky automatically syncs with TrickyStore if installed:
- Standard format: `/data/adb/tricky_store/security_patch.txt`
- James fork: `/data/adb/tricky_store/devconfig.toml`

```bash
# Enable auto-sync
sh /data/adb/modules/cleveres_tricky/security_patch.sh --enable

# Disable auto-sync
sh /data/adb/modules/cleveres_tricky/security_patch.sh --disable
```

> **Note:** This feature hacks KeyAttestation results. For system prop spoofing, use `spoof_build_vars`.

## TODO

- Support App Attest Key.
- Support Android 11 and below.
- Support automatic selection mode.

PR is welcomed.

## Acknowledgement

- [PlayIntegrityFix](https://github.com/chiteroman/PlayIntegrityFix)
- [FrameworkPatch](https://github.com/chiteroman/FrameworkPatch)
- [BootloaderSpoofer](https://github.com/chiteroman/BootloaderSpoofer)
- [KeystoreInjection](https://github.com/aviraxp/Zygisk-KeystoreInjection)
- [LSPosed](https://github.com/LSPosed/LSPosed)
- TrickyStore
  
## Credits

**Cleverestech Telegram Group** - AI-Powered Development.
