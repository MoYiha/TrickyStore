# Identity Refresh

## Purpose

Identity Refresh prepares a new validated application facing identity for the next boot. It rotates configured template and identifier values without creating a mismatch inside the current boot.

## Snapshot model

The service first loads the active identity used by Build fields and attestation. It then creates a separate next boot snapshot. The active file and in memory state are not changed.

During the next early boot phase, the module validates the staged path, file type, size, permissions, master control, and refresh control. It then promotes the staged file atomically before applying Build properties. The service later loads the same promoted file.

This order keeps fingerprint, Build fields, template selection, IMEI, IMSI, ICCID, and serial overrides synchronized for one complete boot.

## Generated values

IMEI and ICCID values include valid checksums. Numeric identifier lengths and serial character sets are bounded. When more than one template exists, the next snapshot selects a different template from the active one.

Manual identity edits discard an older staged snapshot. Disabling Spoof Engine or Identity Refresh before boot prevents an unwanted promotion.

## Scope

Generated telephony values affect supported application APIs only. They do not modify the modem, physical SIM, carrier subscription, EFS storage, or network operator view.

[Return to the project overview](../README.md)
