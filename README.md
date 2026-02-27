# CleveresTricky

**Advanced Spoofing Module for Android**

**Requires Android 12+**

## Features

CleveresTricky provides comprehensive keystore spoofing with the following capabilities:

**Core Features:**
- Binder level system property spoofing (invisible to DroidGuard/GMS)
- KeyMint 4.0 support
- **Advanced RKP Emulation** for MEETS_STRONG_INTEGRITY
- **Dynamic Identity Mutation** (Anti-Fingerprinting)
- Automatic Pixel Beta fingerprint fetching
- Security patch level customization
- Low memory footprint with immediate config release
- **Resource Monitor** to view and manage feature impact on RAM/CPU

**New Advanced Features (v2.0):**
- **Race Condition Engine:** Simulates Keystore TOCTOU (Time-of-Check to Time-of-Use) vulnerabilities to bypass rigorous timing checks.
- **Stealth Daemon:** Native process with `kworker` disguising, memory map sanitization, and anti-debugging protections (`ptrace` detection).
- **Leak Prevention:** Improved Binder FD management to prevent resource exhaustion and detection.

**Integrity Support:**
- MEETS_USE_BRAIN

## Quick Start

1. Flash the module and reboot
2. Place keybox.xml at `/data/adb/cleverestricky/keybox.xml` (optional, for hardware attestation)
3. Configure target packages in `/data/adb/cleverestricky/target.txt` (optional)
4. Enable RKP bypass for STRONG integrity: `touch /data/adb/cleverestricky/rkp_bypass`

Configuration changes take effect immediately.

## Security & Detection Analysis

We maintain a comprehensive document detailing theoretical detection vectors for TEE emulators and our specific mitigations.
This includes analysis of eBPF tracing, DroidGuard timing checks, and memory artifacts.

👉 **Read the full report:** [DETECTION_ANALYSIS.md](DETECTION_ANALYSIS.md)

## Keybox Jukebox (Multi-Key Rotation)

CleveresTricky supports **automatic rotation** of multiple keyboxes to evade detection and distribute load.

1.  **Legacy:** Place a single file at `/data/adb/cleverestricky/keybox.xml`.
2.  **Jukebox Mode:** Place multiple XML files in `/data/adb/cleverestricky/keyboxes/` (e.g., `kb1.xml`, `kb2.xml`).
    - The module loads ALL valid XML files from this folder.
    - Keys are rotated in a round-robin fashion for every attestation request.
    - **WebUI Upload:** You can upload new keyboxes directly via the "Keybox Jukebox" section in the web interface.
    - **Verification:** Click "VERIFY KEYBOXES" in the WebUI to check your keys against Google's Certificate Revocation List (CRL).

## Encrypted Keybox Distribution

Protect your key material using encrypted containers and remote distribution.

### 1. .cbox Files (Local Encrypted)
Distributors can use the **Encryptor App** to create password-protected `.cbox` files.
- **Secure:** Encrypted with AES-256-GCM.
- **Cached:** Once unlocked in the WebUI, the keybox is cached on your device using hardware-backed encryption (Android Keystore). You only enter the password once.
- **Usage:** Place `.cbox` files in `/data/adb/cleverestricky/keyboxes/`. Go to WebUI -> Keyboxes to unlock.

### 2. Remote Servers (Community Distribution)
Automatically fetch and rotate keyboxes from community servers.
- **Auto-Refresh:** Checks for updates periodically (e.g., every 24 hours).
- **Authentication:** Supports Bearer Tokens, Telegram Auth, and more.
- **Smart Priority:** Define server priority to control which keys are used first.
- **Zero Config:** Just add the server URL in the WebUI.

**WebUI Guide:** Check the "📖 Guide" tab in the module's interface for detailed instructions.

## Configuration

### keybox.xml

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

### Build Vars Spoofing

> Requires Zygisk or Zygisk Next

**Strings extracted from:** [https://dl.google.com/developers/android/CANARY/images/factory/comet_beta-zp11.260123.011-factory-9f28f269.zip](https://dl.google.com/developers/android/CANARY/images/factory/comet_beta-zp11.260123.011-factory-9f28f269.zip)

Create/edit `/data/adb/cleverestricky/spoof_build_vars`:

```
MANUFACTURER=Google
MODEL=Pixel 8 Pro
FINGERPRINT=google/husky_beta/husky:15/AP31.240617.009/12094726:user/release-keys
BRAND=google
PRODUCT=husky_beta
DEVICE=husky
RELEASE=15
ID=AP31.240617.009
DISPLAY=AP31.240617.009
INCREMENTAL=12094726
TYPE=user
TAGS=release-keys
SECURITY_PATCH=2024-07-05
BOOTLOADER=...
BOARD=...
HARDWARE=...
HOST=...
USER=...
TIMESTAMP=...
SDK_INT=...
PREVIEW_SDK=...
CODENAME=...
ro.boot.verifiedbootstate=green
ro.boot.flash.locked=1
```

**Device ID Attestation Spoofing (RKP/KeyMint):**
To pass Strong Integrity on devices enforcing RKP rotation, you may need to spoof ID attestation tags. Add these to `spoof_build_vars`:

```
ATTESTATION_ID_BRAND=google
ATTESTATION_ID_DEVICE=husky
ATTESTATION_ID_PRODUCT=husky
ATTESTATION_ID_MANUFACTURER=Google
ATTESTATION_ID_MODEL=Pixel 8 Pro
ATTESTATION_ID_SERIAL=...
ATTESTATION_ID_IMEI=...
```

For Magisk users without Zygisk, remove `/data/adb/modules/cleverestricky/zygisk`.

### Application Specific Spoofing

You can assign specific templates and keyboxes to individual apps.
Create/edit `/data/adb/cleverestricky/app_config` (or use the WebUI "App Config" selector).

**Format:**
```
packageName [TEMPLATE] [keybox_filename]
```

**Using the WebUI:**
1. Navigate to the **Apps** tab.
2. Enter the package name (e.g., `com.google.android.gms`).
3. Select a device template from the dropdown (or keep default).
4. Select a **Keybox** from the dropdown list.
   - *Note:* Custom keybox XML files must be placed in `/data/adb/cleverestricky/keyboxes/` to appear in this list.
5. Click **Add Rule** and then **Save Configuration**.

**Examples:**
```
# Force GMS to use Pixel 8 Pro template and a specific keybox
com.google.android.gms pixel8pro keybox_beta.xml

# Force Wallet to use a different keybox (keep default template)
com.google.android.apps.walletnfcrel null keybox_wallet.xml

# Force Netflix to use Xiaomi 14 template (keep default keybox)
com.netflix.mediaclient xiaomi14 null
```

### Device Templates

Built-in templates available:
- `pixel8pro`, `pixel7pro`, `pixel6pro`
- `xiaomi14`, `s23ultra`, `oneplus11`

Usage in spoof_build_vars:
```
TEMPLATE=pixel8pro
MODEL=Custom Override
```

### Target Configuration

In `/data/adb/cleverestricky/target.txt`:

```
# Standard mode (leaf certificate hack)
io.github.vvb2060.keyattestation

# Generate mode for TEE broken devices (append !)
com.google.android.gms!
```

### Backup & Restore Configuration

You can backup your entire configuration (including keyboxes, spoofing rules, identity settings, and custom templates) and restore it later. This is useful when moving to a new device or re-installing the module.

**How to use:**
1.  Navigate to the **Dashboard** in the WebUI.
2.  Scroll to the **Configuration Management** section.
3.  Click **Backup Config** to download a ZIP file containing your settings.
4.  To restore, click **Restore Config** and select your previously backed-up ZIP file.
    -   *Note:* Restoring will overwrite your current settings and reload the configuration immediately.

## RKP Spoofing (STRONG Integrity)

Remote Key Provisioning spoofing enables MEETS_STRONG_INTEGRITY.

**Enable:**
```bash
touch /data/adb/cleverestricky/rkp_bypass
```

**Disable:**
```bash
rm /data/adb/cleverestricky/rkp_bypass
```

**How it works (The Truth):**
This module uses a sophisticated "Local RKP Proxy" to emulate a secure hardware element. It generates valid, RFC-compliant COSE/CBOR cryptographic proofs signed by a local authority.
- **Goal:** To trick Google's backend into accepting the device as a new, unprovisioned unit or a trusted generic implementation.
- **Reality Check:** This is a "Cat & Mouse" game. While the implementation is technically robust (canonical CBOR, correct P-256 math), Google can theoretically ban the specific implementation pattern or require hardware-root verification.
- **Counter-Measure:** The module features "Dynamic Identity Mutation". The internal root secret rotates automatically every 24 hours, ensuring you don't get stuck with a banned "digital fingerprint".

**Custom keys (optional):**
Place custom remote keys at `/data/adb/cleverestricky/remote_keys.xml`.

### Smart RKP Identity (Custom Keys)

For advanced users, you can provide custom RKP keys to be used instead of auto-generated ones.
This improves resilience by allowing:
1.  **Usage of valid RKP keys** dumped from other devices.
2.  **Smart Rotation:** Supply multiple keys, and the module will randomly rotate between them to avoid pattern detection.
3.  **Hardware Info Overrides:** spoof RKP hardware properties.

Place configuration at `/data/adb/cleverestricky/remote_keys.xml`:

```xml
<RemoteKeyProvisioning>
    <Keys>
        <!-- Repeat <Key> block for multiple keys -->
        <Key>
            <PrivateKey format="pem">
-----BEGIN EC PRIVATE KEY-----
...
-----END EC PRIVATE KEY-----
            </PrivateKey>
            <!-- Optional: Override COSE_Mac0 public key (Base64) -->
            <PublicKeyCose>...</PublicKeyCose>
            <!-- Optional: Override DeviceInfo CBOR (Base64) -->
            <DeviceInfo>...</DeviceInfo>
        </Key>
    </Keys>
    <!-- Optional: Override Hardware Info -->
    <HardwareInfo>
        <RpcAuthorName>Google</RpcAuthorName>
        <VersionNumber>3</VersionNumber>
    </HardwareInfo>
</RemoteKeyProvisioning>
```

If the file is missing, the module falls back to generating fresh random keys for every request.

**Verification:**
Use [Play Integrity API Checker](https://play.google.com/store/apps/details?id=gr.nickas.playintegrity) to confirm all three integrity levels pass.

## Privacy & Identity Mutation (IMEI Changing)

CleveresTricky isn't just about passing integrity checks; it's about **reclaiming your digital identity**. In a landscape where apps track your every move via persistent hardware identifiers (IMEI, IMSI, Serial No), this module offers a powerful "Anti-Fingerprinting" cloak.

**The Cat & Mouse Game:**
Google and app developers constantly evolve their detection methods. Static spoofing is dead. **Dynamic Mutation** is the only way forward. By rotating your device identifiers, you effectively "disappear" and reappear as a new device, breaking the chain of tracking.

### IMEI / Serial Changer
You can change your IMEI, IMSI, Serial Number, and MAC addresses on the fly. This is critical for privacy and for bypassing bans based on hardware IDs.

**Manual Identity Change (WebUI):**
1. Open the WebUI and navigate to the **Spoofing** tab.
2. Scroll to **System-Wide Spoofing**.
3. Enter your desired **IMEI** (Slot 1), **IMSI**, **ICCID**, or **Serial Number**.
   - *Tip:* Use the "GENERATE RANDOM IDENTITY" button to create a valid, Luhn-compliant identity instantly.
4. Click **Apply System-Wide**.
   - *Note:* Changes are applied to the system immediately but may require an app restart (or device reboot) to be fully effective across all layers.

**Randomize on Boot (Set & Forget):**
When enabled, the module will automatically mutate your device identity every time you restart your phone.
- Selects a random certified device template (e.g., Pixel 8, Galaxy S24).
- Generates a valid, random IMEI (Luhn algorithm compliant).
- Generates random Serial Number, Android ID, Wifi MAC, and Bluetooth MAC.
- **Psychological Impact:** You are never the same device twice.

**Enable via WebUI:** Dashboard -> "Randomize on Boot"
**Enable via Shell:** `touch /data/adb/cleverestricky/random_on_boot`

## AutoPIF (Automatic Fingerprint Updates)

Fetches latest Pixel Beta/Canary fingerprints from Google servers.

**Manual execution:**
```bash
# Random device
sh /data/adb/modules/cleverestricky/autopif.sh

# Specific device
sh /data/adb/modules/cleverestricky/autopif.sh --device husky

# List devices
sh /data/adb/modules/cleverestricky/autopif.sh --list
```

**Background updates (24 hour interval, battery optimized):**
```bash
# Enable
touch /data/adb/cleverestricky/auto_beta_fetch

# Disable
rm /data/adb/cleverestricky/auto_beta_fetch
```

## Auto Keybox Revocation Check

Automatically checks all keyboxes against Google's Certificate Revocation List (CRL) every 24 hours.
If a keybox is found to be revoked:
1. It is moved to the `revoked/` folder.
2. A system notification is posted to alert the user.

**Enable:**
```bash
touch /data/adb/cleverestricky/auto_keybox_check
```

**Disable:**
```bash
rm /data/adb/cleverestricky/auto_keybox_check
```

## Security Patch Customization

Create `/data/adb/cleverestricky/security_patch.txt`:

**Simple format:**
```
20241101
```

**Advanced format:**
```
system=202411
boot=2024-11-01
vendor=2024-11-01
```

Special values:
- `no` disables patching for that component
- `prop` keeps system prop consistent

**Security Patch sync:**
```bash
# Enable sync
sh /data/adb/modules/cleverestricky/security_patch.sh --enable

# Disable sync
sh /data/adb/modules/cleverestricky/security_patch.sh --disable
```

## DRM & Streaming Fixes

Fixes playback errors (e.g. Netflix Error 5.7) and Widevine issues on unlocked bootloaders by spoofing system properties.

**Enable via WebUI:** Spoofing -> "Netflix / DRM Fix"
**Enable via Shell:** `touch /data/adb/cleverestricky/drm_fix`

**What it does:**
It overrides the following system properties to mimic a secure environment:
- `ro.netflix.bsp_rev=0`
- `drm.service.enabled=true`
- `ro.com.google.widevine.level=1` (L1 spoof)
- `ro.crypto.state=encrypted`

**Note:** This feature forces specific property overrides globally. You can customize these values by editing the `drm_fix` file in the WebUI Editor.

### DRM ID Generation (Bypass Download Limits)

Some apps track devices using the DRM Device ID (Widevine ID).
If you encounter download limits or need a fresh "streaming identity":

1.  Go to the **Spoofing** tab in WebUI.
2.  In the "DRM / Streaming" section, click **"Regenerate DRM ID"**.
3.  This wipes the DRM provisioning data and forces the system to generate a new, random ID.
    -   *Note:* This will delete downloaded content in streaming apps (Netflix, Spotify, etc).

**Randomize DRM on Boot:**
Enable this toggle to automatically reset the DRM identity on every system startup. This is "battery optimized" as it runs once during initialization and does not require background polling.

### Advanced Methods (Libc Hooking)

This module achieves its "Identity Mutation" and DRM spoofing capabilities through advanced **Library Hooking**.
-   **System Properties:** We hook `libc.so` (via `__system_property_get`) to intercept and modify property reads from native code (DRM libs).
-   **DRM Bypass:** By feeding falsified properties (`ro.crypto.state`, `ro.secure`) directly to the DRM HALs, we trick them into believing they are running in a secure, locked environment without modifying the actual bootloader state.

## Languages

CleveresTricky supports community translations for the WebUI.
For instructions on how to add a language or contribute translations, please see: [LANGUAGES.md](LANGUAGES.md)

## Roadmap
- **Zygisk-less Operation:** Support for standalone mode (Magisk-only/KernelSU native) without Zygisk dependency.
- **Native KernelSU Support:** Enhanced integration for KernelSU users.
- **Enhanced Detection Evasion:** Advanced techniques independent of Zygisk injection.

## Acknowledgements

- [PlayIntegrityFix](https://github.com/chiteroman/PlayIntegrityFix)
- [FrameworkPatch](https://github.com/chiteroman/FrameworkPatch)
- [BootloaderSpoofer](https://github.com/chiteroman/BootloaderSpoofer)
- [KeystoreInjection](https://github.com/aviraxp/Zygisk-KeystoreInjection)
- [LSPosed](https://github.com/LSPosed/LSPosed)

## Credits

Cleverestech Telegram Group

## Support

If you find this project useful, you can support its development: [DONATE.md](DONATE.md)
