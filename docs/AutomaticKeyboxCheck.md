# Automatic Keybox Check

**Language:** **English** | [Türkçe](i18n/tr.md#automatic-keybox-check) | [简体中文](i18n/zh-CN.md#automatic-keybox-check) | [Español](i18n/es.md#automatic-keybox-check) | [Deutsch](i18n/de.md#automatic-keybox-check) | [Русский](i18n/ru.md#automatic-keybox-check) | [Bahasa Indonesia](i18n/id.md#automatic-keybox-check) | [हिन्दी](i18n/hi.md#automatic-keybox-check) | [العربية](i18n/ar.md#automatic-keybox-check)

## Purpose

Automatic Keybox Check keeps authorized key material and revocation state current without continuously scanning storage.

## Lifecycle

The worker starts when Automatic Keybox Check is enabled. Its lifecycle is independent from Spoof Engine because keybox and certificate handling belong to the core Keystore protection path. A service shutdown still cancels scheduled work.

File observers react to normal keybox updates. A low frequency fallback poll covers filesystems where an observer event may be missed. Repeated failures do not create overlapping workers.

## Validation behavior

Every refresh repeats private key correspondence, certificate chain, algorithm, validity, ambiguity, and revocation checks. Valid key material is admitted immediately at boot and in offline environments without blocking on network availability. Revocation enforcement against Google's CRL is strictly conditioned on Automatic Keybox Check: when enabled, revocation status is verified asynchronously once network connectivity is available, safely retiring revoked keys; when disabled, custom or revoked keyboxes remain usable without service rejection. A broken or malformed entry prevents the mixed pool from becoming active.

Cached parsed material is bounded by file count and file size. Unchanged files reuse their verified parse result. Removed files are removed from the cache.

## Resource use

The worker sleeps between scheduled checks and does not busy poll. Disable Automatic Keybox Check when scheduled revocation work is not wanted. Spoof Engine can remain off without stopping core keybox maintenance.

[Return to the project overview](../README.md)
