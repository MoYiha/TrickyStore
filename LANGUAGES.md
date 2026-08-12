# Language Support

**Language:** **English** | [Türkçe](docs/i18n/tr.md#languages) | [简体中文](docs/i18n/zh-CN.md#languages) | [Español](docs/i18n/es.md#languages) | [Deutsch](docs/i18n/de.md#languages) | [Русский](docs/i18n/ru.md#languages) | [Bahasa Indonesia](docs/i18n/id.md#languages) | [हिन्दी](docs/i18n/hi.md#languages) | [العربية](docs/i18n/ar.md#languages)

CleveresTricky ships with nine built in WebUI languages and matching user documentation entry points:

* English
* Türkçe
* 简体中文
* Español
* Deutsch
* Русский
* Bahasa Indonesia
* हिन्दी
* العربية

## WebUI localization

Built in runtime localization is local only and belongs to `module/template/webroot/ux.js`. Language switching does not require a network connection. The fixed WebUI architecture does not permit locale-specific JavaScript or CSS runtime assets.

To add another built in WebUI locale, extend `SUPPORTED`, add its `TRANSLATIONS` catalog, add a localized guide when available, and keep RTL handling correct for right-to-left locales. Do not create a separate locale JS or CSS file.

## Documentation localization

English files are the canonical technical source. User documentation is localized without duplicating the entire source tree:

* `README.md` is the canonical project overview.
* `README.tr.md`, `README.zh-CN.md`, `README.es.md`, `README.de.md`, `README.ru.md`, `README.id.md`, `README.hi.md`, and `README.ar.md` provide localized project overviews.
* `docs/i18n/tr.md`, `zh-CN.md`, `es.md`, `de.md`, `ru.md`, `id.md`, `hi.md`, and `ar.md` provide localized references for every user-facing root Markdown document and every document under `docs/`.
* Every canonical user-facing Markdown document exposes links to the same nine language choices.

The localized reference intentionally keeps stable English anchor identifiers such as `#application-rules`, `#security-model`, and `#web-interface`, so language links remain predictable even when translated headings change.

## Maintenance contract

When user-visible Markdown behavior changes:

1. Update the English canonical document first.
2. Update the matching sections in all eight localized references.
3. Update the localized README files when the project overview or primary workflow changes.
4. Keep code symbols, filenames, commands, configuration keys, API names, and security-sensitive terms technically exact.
5. Treat the English document and source code as authoritative if a translation becomes stale or ambiguous.

Source code, build configuration, CI files, generated artifacts, and internal agent/developer instructions remain English-only. Translating those files would make reviews and tooling less deterministic and is not part of user-facing localization.
