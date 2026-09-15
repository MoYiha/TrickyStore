# Logging and diagnostics

**Language:** **English** | [Türkçe](docs/i18n/tr/LOG.md) | [简体中文](docs/i18n/zh-CN/LOG.md) | [Español](docs/i18n/es/LOG.md) | [Deutsch](docs/i18n/de/LOG.md) | [Русский](docs/i18n/ru/LOG.md) | [Bahasa Indonesia](docs/i18n/id/LOG.md) | [हिन्दी](docs/i18n/hi/LOG.md) | [العربية](docs/i18n/ar/LOG.md)

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
