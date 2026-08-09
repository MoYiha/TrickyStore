# Application Rules

## Purpose

Application Rules assigns a template or a specific keybox source to an eligible application. It adds fine control on top of Application Scope without changing which Android callers are protected.

## Rule behavior

Each rule begins with a validated package pattern. The optional template field selects one known device template. The optional keybox field selects one verified local source. A null field preserves the normal global choice for that part of the request.

Android Package Manager resolves the real packages associated with the calling user identifier. The module never trusts a package name supplied inside an attestation request. Shared Android user identifiers therefore receive one consistent decision.

## Reload and caching

Rules are parsed into a bounded trie. A complete immutable state replaces the older state only after parsing succeeds. The related decision and certificate caches are cleared at the same time.

The WebUI validates package syntax, template names, keybox names, field count, and file size before saving. The service repeats the validation when loading the file.

## Guidance

Use Application Scope first to select the smallest necessary caller set. Add an Application Rule only when one caller needs a different template or authorized key source. Restart that application after a change because it may cache earlier results.

[Return to the project overview](../README.md)
