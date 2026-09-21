# Changelog

## V2.8.5

- **WebUI on Magisk:** the module Action button now opens the full WebUI through a small standalone host app that installs itself on first use. Emergency diagnostics moved into a directly runnable report instead.
- **Local or server keyboxes, your call:** the priority list now shows local and server versions of every tier side by side, so downloads never silently outrank your own files. Previously saved orders are upgraded automatically.
- **Feature Center:** the dashboard now groups the main switches (keybox scope, security patch, identity, invalid-keybox blocking, DRM, camera) in one place with plain explanations.
- **Profiles and patch pages:** a new Profiles tab holds per-app privacy, keybox, and identity sets, plus a dedicated Security Patch page for system, vendor, and boot levels.
- **Smarter app picker:** package search now shows icons with All, User, and System filters plus keyboard navigation.
- **Telephony stays with its targets:** telephony overrides now apply only to apps you explicitly selected instead of every app, so carrier and banking apps keep their genuine values. A separate opt-in under Global Keybox restores the old blanket behavior, with a warning that full-global scope can break carrier features such as VoLTE and VoNR.
- **Disabled means disabled:** turning the region override off now fully clears its leftover modem setting on the next boot instead of leaving it behind.
- **Quieter, steadier boots:** startup no longer reports false errors or retries work that already finished.
