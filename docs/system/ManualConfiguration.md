# Manual Configuration

**Language:** **English** | [Türkçe](../i18n/tr/system/ManualConfiguration.md) | [简体中文](../i18n/zh-CN/system/ManualConfiguration.md) | [Español](../i18n/es/system/ManualConfiguration.md) | [Deutsch](../i18n/de/system/ManualConfiguration.md) | [Русский](../i18n/ru/system/ManualConfiguration.md) | [Bahasa Indonesia](../i18n/id/system/ManualConfiguration.md) | [हिन्दी](../i18n/hi/system/ManualConfiguration.md) | [العربية](../i18n/ar/system/ManualConfiguration.md)

This guide covers file-based manual configuration of CleveresTricky without using the WebUI. The WebUI remains the normal way to change settings; use this guide when you prefer editing files by hand or when the WebUI is unreachable. All module settings and policy files reside in `/data/adb/cleverestricky/`.

---

## 1. Scope & Targeting

* **`target.txt`**: Package names (one per line) targeted for Keystore attestation spoofing.
  ```text
  com.google.android.gms
  com.google.android.gms.unstable
  com.android.vending
  ```
* **`global_mode`**: Empty marker file.
  * **Present**: Global Mode is active (intercepts all non-system, non-root applications).
  * **Absent**: Only applications explicitly listed in `target.txt` are intercepted.
  * *Command:* `touch /data/adb/cleverestricky/global_mode` (enable) or `rm -f /data/adb/cleverestricky/global_mode` (disable).

* **`identity_target.txt`**: Package names targeted for device build property spoofing.
* **`global_identity_mode`**: Empty marker file. If present, applies identity property spoofing globally.

---

## 2. Device Identity & Build Spoofing

* **`spoof_build_vars`**: Device model and build properties to spoof in `KEY=VALUE` format:
  ```properties
  MANUFACTURER=Google
  MODEL=Pixel 8 Pro
  FINGERPRINT=google/husky/husky:14/UQ1A.240105.004/11269998:user/release-keys
  BRAND=google
  PRODUCT=husky
  DEVICE=husky
  RELEASE=14
  ID=UQ1A.240105.004
  INCREMENTAL=11269998
  TYPE=user
  TAGS=release-keys
  ```
* **`security_patch.txt`**: Security patch date (e.g., `2026-03-05`). Leave empty or absent for automatic system date alignment.
* **`boot_props_mode`**: Controls bootloader property simulation (`auto`, `force`, or `disable`).

---

## 3. Hardware Keybox

* **`keybox.xml`**: Place your hardware-backed attestation keybox XML directly at `/data/adb/cleverestricky/keybox.xml`. Ensure permissions are restricted (`chmod 600`).
* **`keyboxes/`**: Directory for placing multiple keybox files.
* **Uploads never overwrite:** Dropping or pasting a keybox is stored under the supplied filename, or as `keybox.xml` when no filename is supplied; when that name is already taken, the next free name (`keybox2.xml`, `keybox3.xml`, ...) is used automatically.
* **`disabled_keyboxes`**: Pool opt-out list. Each line holds one `scope:filename` identifier (`keyboxes:keybox2.xml`, `root:keybox.xml`) matching the filenames shown in the WebUI Keybox panel. Listed keyboxes stay visible and manageable but are never loaded into the attestation pool. The WebUI Disable and Enable buttons read and write this file, so you can also maintain it by hand.

---

## 4. DRM & Privacy Scope

* **`drm_packages.txt`**: Package names for media applications that should bypass Keystore interception to preserve Widevine L1 hardware DRM:
  ```text
  com.netflix.mediaclient
  com.amazon.avod.thirdpartyclient
  com.disney.disneyplus
  ```

---

## 5. Feature Flags (Marker Files)

Enable features by creating the marker file (`touch <file>`), or disable them by removing it (`rm -f <file>`):

| Marker File | Effect When Present |
| :--- | :--- |
| `spoof_enabled` | Activates the core identity spoofing engine. |
| `spoof_build_identity` | Activates build property spoofing. |
| `auto_keybox_check` | Automatically validates keyboxes and checks revocation status. |
| `drm_passthrough` | Enables DRM passthrough protection for packages in `drm_packages.txt`. |
| `hide_sensitive_props` | Conceals root, debugging, and bootloader status properties. |
| `tee_broken_mode` | Legacy migration/compatibility state. When present, the service retains legacy migration handling; core protection is unchanged and the fail-closed circuit breaker activates separately upon hardware TEE communication failure. |
| `debug_logging` | Enables verbose runtime logging to `native_runtime.log`. |

---

## Applying Changes & Verification

### Applying Configuration Changes
Changes made in the WebUI apply through the normal runtime path. If you edited configuration or marker files by hand, rebooting the device is recommended afterwards:
```sh
su -c "reboot"
```

### Inspecting Runtime Logs
To check whether the CleveresTricky daemon and interceptor are operating correctly:
```sh
su -c "cat /data/adb/cleverestricky/native_runtime.log"
```

### Verifying Running Processes
```sh
su -c "ps -A | grep -E 'cleverestrickyd|cleverestricky_backend'"
```

### Generating Diagnostic Bug Reports
To gather a complete diagnostic archive (on Magisk the module Action button opens the WebUI instead, so run this file directly):
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
The resulting archive will be created in `/data/adb/cleverestricky/bugreports/`.
