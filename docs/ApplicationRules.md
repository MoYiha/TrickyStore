# Application Rules

## Purpose

Application Rules assigns a template, a specific keybox source, or an application privacy policy to an eligible application. A valid rule is itself an explicit application target, so a separate scope entry is not required.

## Rule behavior

Each rule begins with a validated package pattern. The optional template field selects one known device template. The optional keybox field selects one verified local source. A null field preserves the normal global choice for that part of the request.

Privacy mode `inherit` keeps the global identity policy. Privacy mode `isolate` derives stable application scoped IMEI, IMSI, ICCID, MEID, phone, serial, and supported attestation identifiers from a protected random seed. Values remain stable across service restarts and differ between unrelated application identities. Privacy mode `redact` returns blank supported identity values while preserving Android permission failures.

Telephony identity policy works independently. Attestation identity replacement requires an active verified keybox because the modified certificate chain must be signed. Without one, the original attestation chain remains unchanged.

Shared Android user identifiers use the sorted set of resolved packages as one derivation context. Redaction takes precedence over isolation when packages sharing one user identifier request different policies.

Android Package Manager resolves the real packages associated with the calling user identifier. The module never trusts a package name supplied inside an attestation request. Shared Android user identifiers therefore receive one consistent decision.

## Reload and caching

Rules are parsed into a bounded trie. A complete immutable state replaces the older state only after parsing succeeds. The related decision and certificate caches are cleared at the same time.

Caller package and identity decisions expire with the Package Manager cache so Android user identifier reuse cannot carry an older application policy into a newly installed package.

The WebUI validates package syntax, template names, keybox names, privacy modes, field count, duplicates, empty rules, and file size before saving. The service repeats the validation when loading the file. The privacy seed is stored as root only configuration and is included only inside an encrypted backup.

## Guidance

Use Application Scope for callers that need the global policy. Add an Application Rule when one caller needs a different template, authorized key source, or privacy identity. Restart that application after a change because it may cache earlier results.

The privacy policy covers the telephony and attestation identity paths implemented by the module. It does not claim to block sensors, clipboard access, location, VPN checks, accessibility checks, or arbitrary code inside another application process.

[Return to the project overview](../README.md)
