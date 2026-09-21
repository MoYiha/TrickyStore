# CleveresTricky - Strong Integrity Guide

**Language:** **English** | [Türkçe](../i18n/tr/security/StrongIntegrityGuide.md) | [简体中文](../i18n/zh-CN/security/StrongIntegrityGuide.md) | [Español](../i18n/es/security/StrongIntegrityGuide.md) | [Deutsch](../i18n/de/security/StrongIntegrityGuide.md) | [Русский](../i18n/ru/security/StrongIntegrityGuide.md) | [Bahasa Indonesia](../i18n/id/security/StrongIntegrityGuide.md) | [हिन्दी](../i18n/hi/security/StrongIntegrityGuide.md) | [العربية](../i18n/ar/security/StrongIntegrityGuide.md)

## Overview

This guide provides step-by-step instructions for passing **Google Play Integrity** (`MEETS_STRONG_INTEGRITY`, `MEETS_DEVICE_INTEGRITY`, `MEETS_BASIC_INTEGRITY`) using **CleveresTricky** on Android 12 through Android 17 with **KernelSU**, **APatch**, or **Magisk** (on Magisk, the WebUI opens from the module Action button via the standalone WebUI host app).

Whether you want to ensure hardware-backed attestation compatibility for banking apps, games, or Google Wallet, follow the simple instructions below for your specific ROM type.

---

## Quick Setup by ROM Type

### 1. Official Stock ROM (Pixel, Xiaomi/HyperOS, Samsung/OneUI, OnePlus/OxygenOS, etc.)

* **Step 1:** Install the latest CleveresTricky module ZIP in KernelSU, APatch, or Magisk.
* **Step 2:** Open the CleveresTricky WebUI from your module manager (on Magisk, from the module Action button via the standalone WebUI host app) and add a valid **Keybox**.

**That is all you normally need!** Keep all other settings at their default values.

---

### 2. Official ROM with an Outdated Security Patch Level

If your official firmware has not received monthly security updates and has a very old Android security patch date:

* **Step 1:** In the CleveresTricky WebUI, go to the **Dashboard**.
* **Step 2:** Enable **Security Patch**.
* **Step 3:** Set the Security Patch mode to **Auto**.

---

### 3. AOSP ROM (LineageOS, crDroid, PixelExperience, Evolution X, etc.)

* **Step 1:** In the CleveresTricky WebUI, open the **Dashboard**.
* **Step 2:** Enable **Identity** (Spoof Engine).
* **Step 3:** Spoof your device fingerprint - you can select **Auto Pixel Identity** or pick a certified device model template.

> [!WARNING]
> **Memory notice:** Enabling Identity and automatic fingerprint spoofing may slightly increase RAM usage due to dynamic runtime property evaluation.

---

### 4. Custom ROMs with Non-standard Keystores

> [!IMPORTANT]
> **Custom ROMs are not officially supported.**
> If your Custom ROM has a broken vendor Keystore implementation or native hardware attestation does not work, please use legacy fallback methods instead.

---

## Keybox Verification and Management

### Tools and Resources

* **Online Keybox Checker:** Verify certificate validity, chain integrity, and revocation status online at [https://keybox.tryigit.dev/checker](https://keybox.tryigit.dev/checker).
  
* **Keybox Download & Information:** [https://keybox.tryigit.dev/](https://keybox.tryigit.dev/)

### How to Import Your Keybox

1. Open the **CleveresTricky WebUI** from KernelSU, APatch, or Magisk (Action button).
2. Navigate to **Keybox Manager**.
3. Tap **Import / Upload** and select your `.xml` or encrypted `.cbox` file.

> [!NOTE]
> **No manual copying required:** You do **not** need to manually copy `keybox.xml` into legacy TrickyStore directories (such as `/data/adb/tricky_store/keybox.xml`). CleveresTricky manages, encrypts, and isolates your keybox pool automatically.

---

## Legal Disclaimers and Community Notice

* **Independent Community Project:** CleveresTricky is an independent open-source research and compatibility project maintained by the **CleveresTricky Community**. It is **not** affiliated with, endorsed by, or sponsored by Google LLC or the Android Open Source Project (AOSP).
* **Google Policy and Detection Changes:** Google continuously and unpredictably modifies the **Google Play Integrity API**, server-side verification heuristics, device certification lists, and key revocation databases without notice. An attestation verdict (`MEETS_STRONG_INTEGRITY`) that passes today can be altered or invalidated at any time by upstream Google changes.
* **No Guarantees / Local Compatibility Only:** CleveresTricky optimizes and repairs local Android keystore compatibility paths; it **cannot** guarantee remote verification verdicts or permanent bypass of server-side security policies. Use this software at your own risk.
* **Authorized Material Only:** No usable keybox, certificate, or private attestation key is bundled with CleveresTricky. Users must only supply key material they own or are legally authorized to test.

---

[Return to the project overview](../README.md)
