# Web Interface

## Purpose

The Web Interface provides one mobile control surface for runtime state, identity, application rules, keyboxes, profiles, encrypted backup, logs, and validated configuration editing.

## Mobile behavior

The layout uses touch sized controls, responsive panels, compact status summaries, password visibility controls, progress states, and clear result notifications. Tabs support keyboard focus and accessibility state. Long operations use bounded request timeouts and prevent duplicate button actions while work is active.

Changes that require reboot are identified separately from live runtime controls. Failed writes restore the visible toggle state instead of leaving the screen out of sync with the service.

## Local access protection

The server binds only to the loopback interface. KernelSU or APatch opens it with a random protected token. Requests require a constant time token comparison. Host and Origin headers are checked, rate limits are bounded, and responses include same origin and content security headers.

The token is stored in a root only regular file. The browser keeps it in session storage for the current WebUI session. Opening the address without the module action token does not grant access.

## Input handling

Every endpoint accepts a fixed method and bounded request form. File names, paths, JSON fields, package rules, templates, identifiers, keybox input, source settings, and backup data are validated again on the service side.

Unsafe paths, symbolic links, oversized input, duplicate archive entries, unknown settings, and malformed values are rejected. A visible success response is returned only after the service completes the requested write or operation.

## Recommended use

Open the interface from the module Action screen. Begin on Dashboard, apply Daily Compatibility, then configure Applications and Keyboxes. Use Logs after each material change and restart an application that may cache old results.

[Return to the project overview](../README.md)
