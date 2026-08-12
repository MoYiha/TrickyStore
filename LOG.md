# Logging and diagnostics

**Language:** **English** | [Türkçe](docs/i18n/tr.md#logging) | [简体中文](docs/i18n/zh-CN.md#logging) | [Español](docs/i18n/es.md#logging) | [Deutsch](docs/i18n/de.md#logging) | [Русский](docs/i18n/ru.md#logging) | [Bahasa Indonesia](docs/i18n/id.md#logging) | [हिन्दी](docs/i18n/hi.md#logging) | [العربية](docs/i18n/ar.md#logging)

CleveresTricky writes diagnostics to Android logcat; it does not store a separate plaintext log file.

```bash
adb logcat -s cleverestricky CleveresTricky
```

Useful startup markers are:

- `Welcome to Service!`
- `Web server on port ...`
- `libbinder ioctl hook installed successfully`
- `Keystore Binder interceptor registered`
- `TEE SecurityLevel interceptor registered`

Errors such as `TAMPER DETECTED`, `Binder ABI validation failed`, a rejected keybox, or an injector timeout are actionable. Release builds retain informational, warning, and error logs; debug builds additionally emit verbose native diagnostics.

For a clean capture:

```bash
adb logcat -c
adb shell su -c 'setprop ctl.restart keystore2'
adb logcat -d -s cleverestricky CleveresTricky
```

Do not publish logs without reviewing them. Although credentials and WebUI tokens are not intentionally logged, filenames, package names, device properties, and process identifiers may still be sensitive.
