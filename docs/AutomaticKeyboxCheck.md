# Automatic Keybox Check

## Purpose

Automatic Keybox Check keeps authorized key material and revocation state current without continuously scanning storage.

## Lifecycle

The worker starts only when Spoof Engine and Automatic Keybox Check are both enabled. It stops when either control is disabled. A service shutdown also cancels scheduled work.

File observers react to normal keybox updates. A low frequency fallback poll covers filesystems where an observer event may be missed. Repeated failures do not create overlapping workers.

## Validation behavior

Every refresh repeats private key correspondence, certificate chain, algorithm, validity, ambiguity, and revocation checks. New material is not activated when revocation data is unavailable. A broken entry prevents the mixed pool from becoming active.

Cached parsed material is bounded by file count and file size. Unchanged files reuse their verified parse result. Removed files are removed from the cache.

## Resource use

The worker sleeps between scheduled checks and does not busy poll. Disabling Spoof Engine stops it entirely. Targeted mode and a small keybox set provide the lowest routine memory and processor cost.

[Return to the project overview](../README.md)
