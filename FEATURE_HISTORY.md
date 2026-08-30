# Feature History & Prior Art

This document records selected feature history for CleveresTricky so that users can trace when major capabilities were publicly implemented. It is intended as historical documentation and attribution context, not as a claim that another project copied source code.

## Device identity & attestation

- **#79 — App-specific configuration and `ATTESTATION_ID_*` handling (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
  Introduced application-scoped configuration and attestation identity handling, including fields such as IMEI and Serial.

- **#139 — Randomized device identity (2026-02-05)**
  https://github.com/tryigit/CleveresTricky/pull/139
  Added randomized identity generation, including IMEI and Serial, with WebUI-triggered generation.

- **#871 — App-facing dual-SIM/device identity controls (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/871
  Expanded identity handling to IMEI, IMEI2, MEID, IMSI, ICCID, phone number and Serial, with application/profile scope and runtime lifecycle handling.

## Keybox / attestation infrastructure

- **#77 — Multi-keybox management and rotation (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/77
  Added multiple keybox loading, rotation and WebUI management.

- **#79 — Keybox verification / attestation identity work (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
  Included keybox/attestation handling alongside application-specific identity configuration.

## Native / Rust architecture

- **#876 — Rust/native interceptor architecture and lifecycle work (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/876
  Major native/Rust migration covering injector/interception infrastructure and runtime lifecycle handling.

## Additional module features

The project has also accumulated related module capabilities through subsequent work, including profile/template handling, application scoping, runtime hook lifecycle controls, identity isolation/redaction, RKP/DRM-related handling, WebUI management, and StrongBox/attestation integration work.

- **#376** — profile/configuration and related module evolution
  https://github.com/tryigit/CleveresTricky/pull/376
- **#476** — identity/module evolution
  https://github.com/tryigit/CleveresTricky/pull/476
- **#618** — module/profile evolution
  https://github.com/tryigit/CleveresTricky/pull/618
- **#908** — subsequent identity/module work
  https://github.com/tryigit/CleveresTricky/pull/908
- **#909** — subsequent module work
  https://github.com/tryigit/CleveresTricky/pull/909
- **#910** — subsequent module work
  https://github.com/tryigit/CleveresTricky/pull/910
- **#952** — subsequent module/profile work
  https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — StrongBox to TEE redirection and attestation security-level harmonization**
  https://github.com/tryigit/CleveresTricky/pull/1132
  This change was subsequently reverted; the current `master` state therefore does not include it.

## Historical note

The links above are direct GitHub records of the project's own development history. Dates and implementation details should be verified against the linked pull requests and commits. Similar functionality across projects does not by itself establish source-code copying or a license violation.
