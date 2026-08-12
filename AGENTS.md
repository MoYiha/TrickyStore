# CleveresTricky Versioning Rules

When bumping versions for the CleveresTricky module, do NOT touch `update.json`. You must leave `update.json` completely unmodified.
Only update `build.gradle.kts` (e.g., `val verName by extra(...)`) and any other necessary files, but explicitly skip `update.json`.

## WebUI architecture contract

The runtime WebUI file layout is fixed. AI agents and human contributors must extend the existing owners instead of creating extra JavaScript or CSS layers.

Allowed runtime files under `module/template/webroot`:

- `index.html`: static HTML, base/static CSS, and the legacy inline controller that has not yet been extracted. Do not create additional HTML entry points or standalone CSS files.
- `bridge.js`: native KernelSU/APatch bridge, host commands, file transfer, external intents, and minimal WebUI bootstrap only. Do not add policy rendering, localization catalogs, or page-specific UI state here.
- `policy.js`: policy/state API integration and policy-owned dynamic controls/pages only. Do not wrap or replace global navigation functions and do not own general UX/community-link behavior.
- `ux.js`: the single general UX/localization/presentation owner. Locales, guide presentation, community-link behavior, compatibility presentation, and general UX enhancements belong here.
- `LOCALES.md`: localization documentation only; it is not a runtime asset.

Do not add files such as `*-patch.js`, `*-fix.js`, `*-test.js`, `*-overlay.js`, `*-ux.js`, temporary CSS files, experiment bundles, or feature-specific runtime JS/CSS files. Tests must stay outside `module/template/webroot` and must never become runtime assets.

When changing WebUI behavior:

1. Identify the existing owner above and edit that file.
2. Prefer deleting obsolete compatibility layers over adding a new layer.
3. One DOM surface must have one owner. Do not attach competing handlers from multiple files to the same control.
4. Do not monkey-patch `window.switchTab`, `window.toggle`, or another global controller from multiple files. If a compatibility hook is unavoidable, it must have exactly one documented owner.
5. Keep the runtime JS set fixed to `bridge.js`, `policy.js`, and `ux.js`; keep standalone runtime CSS count at zero unless this contract is intentionally redesigned in the same change.
6. Do not reintroduce `ux-base.js`; its contents were consolidated into `ux.js`.

## Documentation localization contract

The built in user-facing language set is fixed to English, Türkçe, 简体中文, Español, Deutsch, Русский, Bahasa Indonesia, हिन्दी, and العربية unless the supported WebUI locale set is intentionally changed in the same work.

English is the canonical technical documentation language. User-facing documentation includes `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `DONATE.md`, `LANGUAGES.md`, `LOG.md`, `THEME.md`, and Markdown documents directly under `docs/`.

- Localized project overviews live in `README.<locale>.md`.
- Localized documentation references live in `docs/i18n/<locale>.md` and use stable English anchor IDs.
- Every canonical user-facing Markdown document must expose the same nine-language navigation.
- When a user-visible Markdown document changes materially, update the matching sections in all localized references and update localized README files when the project overview changes.
- Preserve code symbols, API names, config keys, commands, filenames, security behavior, and numeric limits exactly inside translations.
- Do not localize source code, build files, CI configuration, generated files, or internal agent/developer instructions. Those remain English for deterministic tooling and review.

## Branch Lifecycle Rules

Feature, fix, experiment, and AI-generated branches are temporary and must not be kept after their work is integrated.

- After a pull request is successfully merged into `master`, delete its source branch immediately.
- Remove stale branches whose changes have already been merged into `master`.
- Do not delete `master` or any branch that still contains unmerged work.
- Keep a merged branch only when there is an explicit, documented reason for it to remain long-lived.
