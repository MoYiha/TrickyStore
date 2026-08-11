# Web Interface

## Purpose

The Web Interface provides one mobile control surface for runtime state, identity, application rules, keyboxes, profiles, encrypted backup, logs, and validated configuration editing.

## Mobile behavior

The layout uses touch sized controls, responsive panels, compact status summaries, password visibility controls, progress states, and clear result notifications. Tabs support keyboard focus and accessibility state. Long operations use bounded request timeouts and prevent duplicate button actions while work is active.

Changes that require reboot are identified separately from live runtime controls. Failed writes restore the visible toggle state instead of leaving the screen out of sync with the service.

The application selector reads Package Manager through the service and uses the module manager package API as a bounded fallback when the service query is temporarily unavailable.

## Native access protection

KernelSU or APatch loads the packaged `webroot` directly. The page uses the module manager native command API and never opens a local TCP port. A small Rust bridge moves bounded requests through root only queue directories to the existing service router.

Request identifiers use operating system randomness. Queue files are regular files with root only modes, published atomically, claimed before execution, removed after use, and expired when stale. The bridge accepts only fixed API paths, methods, parameter shapes, upload fields, response sizes, timeouts, and safe export names. The page uses a restrictive content security policy.

## Input handling

Every endpoint accepts a fixed method and bounded request form. File names, paths, JSON fields, package rules, templates, identifiers, keybox input, source settings, and backup data are validated again on the service side.

Unsafe paths, symbolic links, oversized input, duplicate archive entries, unknown settings, and malformed values are rejected. A visible success response is returned only after the service completes the requested write or operation.

## Recommended use

Open the interface from the module WebUI button in KernelSU or APatch. Begin on Dashboard, apply Daily Compatibility, then configure Applications and Keyboxes. Use Logs after each material change and restart an application that may cache old results.

[Return to the project overview](../README.md)
