# WebUI locales

CleveresTricky WebUI translations are local-only and live in `ux.js`; switching language never requires network access.

Current built-in locales are English, Türkçe, 简体中文, Español, Deutsch, Русский, Bahasa Indonesia, हिन्दी, and العربية. The repository user documentation mirrors the same nine-language set; see [`LANGUAGES.md`](../../../LANGUAGES.md).

## Add a language

1. Add `[locale, displayName]` to `SUPPORTED` in `ux.js`.
2. Add a `TRANSLATIONS[locale]` catalog for every canonical visible UI key. English is only a fail-safe for unforeseen runtime text; a shipped locale must not rely on it for known cards, controls, status rows, dialogs, placeholders, or accessibility labels.
3. Add `GUIDE[locale]` when a localized guide is available; otherwise the English guide is used.
4. Keep RTL locales covered by the `html[dir="rtl"]` rules and extend the direction rule if another RTL locale is added.
5. Do not create a locale-specific JS or CSS file. Localization must remain inside `ux.js` and use the existing WebUI file set.
6. Add the locale to the repository documentation navigation and localized documentation structure described in `LANGUAGES.md`.
7. Extend `module/webui-tests/localization.test.js`; its complete-catalog and dynamic-message contract must pass for every non-English locale.
8. Run `node --check module/template/webroot/ux.js` and the existing WebUI checks before opening a PR.

The language selector is rendered on Dashboard immediately after Feature Center, and the chosen locale is persisted in `localStorage` under `cleverestricky.language.v1`.
