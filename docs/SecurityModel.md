# Security Model

## Trust boundaries

CleveresTricky runs with root service access and injects a bounded native library into selected Android system processes. Root, the operating system, KernelSU or APatch, the installed module files, and explicitly authorized key material are therefore trusted parts of the local environment.

Applications, Binder request content, uploaded files, remote source responses, configuration edits, archive entries, package rules, template data, file paths, and network metadata are treated as untrusted input.

## File protection

The configuration root is required to be a real directory owned by root. Sensitive files use root only modes. Reads and writes reject symbolic links where a path can affect protected material. Atomic writes prevent readers from observing partial configuration.

Keybox, backup, template, rule, and identity input has fixed count and size limits. Archive extraction uses an allowlist and validates all staged entries before committing them.

## Runtime protection

Caller policy uses the Android user identifier observed through Binder and packages resolved by Package Manager. System identifiers and RKP infrastructure are protected. Unknown package resolution fails closed.

The native parser validates the live Binder ABI before hooks are installed. Binder response bytes pass through a bounded kernel validated copy before Rust parses them. Rust bounds every stream, layout, ancillary message, process name, path, argument, and remote memory plan. Raced, unreadable, unexpected, or malformed input passes through or stops activation without guessing.

The injector accepts only the known `entry` and `resume` symbols, supported stopped process names, executable platform symbol mappings, a root owned regular library that is not writable by group or other users, and an open file descriptor transferred through a random local socket. Its bounded stack journal restores explicit data plus a call stack guard before registers and detach.

The injected Binder control object can be discovered only through a driver reported root caller. Every registration, removal, park, and clear command checks the Binder calling user again. Disabling the engine clears registered callbacks before the hook enters its atomic paused path.

## Web protection

The WebUI listens only on loopback and requires a random token. Host, Origin, method, path, rate, request size, and content validation run before privileged operations. Responses use restrictive browser security headers.

## Limits

A hostile root process can modify the runtime and read unlocked secrets. Userspace code cannot physically relock a bootloader, repair verified boot, alter hardware fuses, change TEE measurements, rewrite the modem, or create a hardware trust root.

Remote services can change policy independently. The module cannot guarantee acceptance outside the device.

[Return to the project overview](../README.md)
