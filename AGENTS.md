# CleveresTricky Versioning Rules

When bumping versions for the CleveresTricky module, do NOT touch `update.json`. You must leave `update.json` completely unmodified.
Only update `build.gradle.kts` (e.g., `val verName by extra(...)`) and any other necessary files, but explicitly skip `update.json`.

## Repository-wide engineering contract

CleveresTricky must be treated as one system, not as a collection of unrelated files. A change that looks local may cross Kotlin/Android, module packaging, WebUI, native Binder, Rust backend, cache/serialization, backup/restore, or CI boundaries.

Before editing code, agents must inspect the actual current repository tree and the relevant workflows. Do not assume an older directory layout, an earlier conversation summary, comments, or this file are more authoritative than the checked-out source. If documentation and the current tree disagree, investigate the discrepancy instead of coding from the stale description.

For every non-trivial bug fix or behavior change:

1. Identify the observable failure and the invariant that should hold.
2. Search all references to the affected symbol, format, config key, cache entry, file type, API, or lifecycle object before choosing the fix.
3. Trace both producers and consumers. Include alternate paths such as direct input vs ZIP/archive input, fresh data vs cached data, startup vs runtime refresh, success vs recovery, UI vs service, and Kotlin vs Rust/native boundaries when applicable.
4. Fix the root cause at the narrowest shared invariant. Do not patch only the first caller if sibling paths can violate the same invariant.
5. Add or update a regression test that fails for the original bug and passes for the intended behavior. A bug fix without regression coverage requires a concrete reason in the PR description.
6. Audit neighboring edge cases after the fix: failure, cancellation, retry, restart, concurrent events, stale cache state, malformed input, partial reads/writes, zero/maximum/over-limit sizes, duplicate names, symlinks/path changes, and cleanup on early return.
7. Re-search references after editing to verify no old bypass, duplicate implementation, compatibility accessor, or alternate path still violates the invariant.
8. Inspect the final diff as a whole. Broadly audit; narrowly patch. Do not mix unrelated cleanup into a bug fix merely because the file is already open.

### Mandatory security and resource-boundary reasoning

- Enforce size/count/resource limits before expensive work such as hashing, parsing, decrypting, decompressing, allocating, or reading an entire stream. Metadata checks alone are not sufficient when a file/stream can change during the operation; the operation itself must remain bounded.
- Security-sensitive state must fail closed. If data is signed/encrypted/trusted only under a verification condition, no alternate accessor, cache restore path, archive path, compatibility API, or lazy getter may expose the protected data before that condition is satisfied.
- Secret or sensitive buffers must be wiped on success, rejection, exception, and early-return paths when the surrounding code already guarantees wiping semantics.
- Cancellation and exception paths are first-class behavior. A scheduler, worker, observer, retry loop, or lifecycle owner must not leave stale ownership state, lose pending work, resurrect cancelled work, or overwrite a replacement worker.
- Cache identity must include every property that changes the trust or interpretation of cached data. Never serialize process-local/opaque handles as if they were portable key material.
- File handling must preserve `NOFOLLOW_LINKS`/symlink protections and bounded I/O. Do not replace secure file helpers with convenient unbounded APIs.

## No-shortcut / no-partial-fix policy

Agents must not optimize for the smallest textual patch or for making one failing check disappear. Optimize for a correct invariant with the smallest complete implementation.

Do not:

- stop after the first plausible fix without checking sibling paths and callers;
- weaken, skip, delete, quarantine, or rewrite a valid regression test merely to get CI green;
- relax security thresholds, parser limits, hardening checks, warnings-as-errors, or lint rules to hide a defect;
- change expected values to match broken behavior unless the behavior change is intentional and justified by the project contract;
- swallow exceptions, add broad catch-and-ignore blocks, or convert failures to empty/default success values without proving that is the intended API behavior;
- add duplicate `*-fix`, `*-patch`, compatibility wrappers, second owners, or temporary runtime layers when the existing owner should be corrected;
- leave required tests, cleanup, or cross-path auditing as `TODO` follow-up work when it belongs to the same fix;
- claim a change is safe because it "should compile" or because a similar path passed previously;
- create temporary GitHub Actions workflows or CI jobs whose purpose is to edit/commit/push source code to the PR branch. CI validates code; it must not be used as a branch-mutation workaround. Make source changes through normal commits so the final PR head is authored and reviewable normally;
- use CI results from an older/staging/bot-generated head as evidence that the final head is green. Only checks attached to the exact final commit count.

If a build/test failure appears, read the failing job/step output and fix the root cause. Do not repeatedly guess-edit-push. Distinguish source failures from infrastructure failures and record that distinction when relevant.

## Mandatory preflight before PR or direct push

Agents must run the checks that correspond to the changed scope before opening a PR or directly pushing a requested change. Do not intentionally use GitHub CI as the first compile/test attempt when the equivalent check can be run locally or in the available execution environment.

Always:

- inspect `git diff --check` (or the equivalent diff validation) and the complete changed-file list;
- verify there are no temporary patch scripts, generated debug artifacts, local secrets, test fixtures in runtime directories, or accidental workflow files in the final diff;
- verify the branch is based on the intended current `master` and re-check conflicts/stale assumptions if `master` moved during the work.

For Gradle/Kotlin/Java/XML changes, match the Build/Security workflows as applicable:

```bash
./gradlew ktlintCheck --warning-mode=fail --console=plain
./gradlew :service:lintDebug :stub:lintDebug :encryptor-app:lintDebug --warning-mode=fail --console=plain --continue
./gradlew :service:testDebugUnitTest :stub:testDebugUnitTest :encryptor-app:testDebugUnitTest --warning-mode=fail --console=plain --stacktrace --no-build-cache
```

When only a subset is genuinely unaffected, agents may run the workflow-equivalent affected subset, but shared Gradle/build logic or cross-module behavior requires the full set above.

For Rust changes:

```bash
cd rust
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace --verbose
```

Also run targeted native/Binder regression tests for the touched invariant instead of relying only on the workspace test sweep.

For WebUI changes:

```bash
for script in module/template/webroot/*.js; do node --check "$script"; done
for test_file in module/webui-tests/*.test.js; do node "$test_file"; done
```

For installer/shell changes, run the matching shellcheck and extraction/security tests from the workflows. For native/module packaging changes, run the module build/hardening path, not only source-level unit tests.

If an environment cannot run a required check, state exactly which check could not be run and why. Do not silently substitute confidence for execution.

## CI failure discipline

A PR is not "green enough" because one workflow passed. Evaluate the exact final head commit across every workflow selected by the repository's change detection.

When CI fails:

1. Open the first actionable failing job and inspect the actual log/report.
2. Reproduce the same command locally when possible.
3. Fix the underlying source/test/config issue, not the CI symptom.
4. Add or strengthen regression coverage when the failure exposed a missing invariant.
5. Re-run the directly affected local checks before pushing another commit.
6. Re-check the entire final workflow set after the new head is created.

Avoid commit churn. Multiple speculative "maybe this fixes CI" commits are a signal to stop and inspect the failing command more deeply.

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

## Native / TEE regression guardrails

Native runtime health, Binder behavior, TEE timing, and attestation state are release-critical. Agents must treat regressions in these areas as blockers rather than compatibility quirks.

- On KernelSU/APatch device tests, require `native_state=active`, `native_alive=true`, and the expected interceptors to activate when their features are enabled. A yellow-to-red runtime-health transition is a blocker.
- TEE timing-side-channel checks must stay below the project threshold of `1.1x`. A positive result at or above the threshold must be investigated before merge/release; do not suppress the warning or relax the threshold to make a build pass.
- Treat bootloader / Verified Boot / attestation-state regressions as release blockers. Do not trade attestation correctness for permissive spoofing or compatibility shortcuts.
- Mount-namespace differences may change device/inode identity between processes. Keep canonical platform-location matching plus fail-closed ELF ABI/build-ID validation; never fall back to same-basename-only symbol resolution.
- Binder/native hot paths are performance-sensitive. Avoid new per-call syscalls, unbounded allocations, repeated parsing, or expensive zeroization in hot paths without measurement. Keep CPU, RSS, Binder latency, and TEE latency close to the last known-good baseline.
- Preserve fail-closed Binder FD classification, bounded parser limits, coherent transaction writeback, ptrace signal handling, pointer-log redaction, temporary-buffer wiping, and cleanup behavior.
- Future Android API support must be validated against the actual compiled Binder UAPI/layout. Struct size or API number alone is not sufficient proof of compatibility.
- Changes touching ptrace, Binder, process memory, FD transfer, symbol resolution, TEE/attestation behavior, or boot/Verified Boot state need targeted regression tests.

## WebUI localization release guardrails

- Every first-party user-visible string added to the English catalog must be added to all built-in locales in the same change, including dynamic messages, dialogs, placeholders, errors, progress states, and accessibility labels.
- Non-English locales must not silently fall back to English for first-party UI text, except intentionally untranslated technical identifiers or protocol names.
- Keep all locale catalogs at identical key coverage and retain automated full-catalog localization tests.

## Merge / release verification

- Before merge, require the Build and Security Regression workflows to pass and resolve actionable Codex/review findings, unless the repository owner/maintainer explicitly overrides that requirement in the current instruction. Never infer an override from urgency or confidence.
- When native/module paths are selected, Native Hardening and the module artifact build are part of the required final-head verification unless explicitly overridden by the maintainer.
- For release candidates, verify module archive structure, checksums, native artifact hardening, signed APK verification, and device-level native/TEE behavior.
- Do not update `update.json`, release URLs, hashes, or release metadata until the release artifact actually exists and those values are verified from the published artifact.

## Branch Lifecycle Rules

Feature, fix, experiment, and AI-generated branches are temporary and must not be kept after their work is integrated.

- After a pull request is successfully merged into `master`, delete its source branch immediately.
- Remove stale branches whose changes have already been merged into `master`.
- Do not delete `master` or any branch that still contains unmerged work.
- Keep a merged branch only when there is an explicit, documented reason for it to remain long-lived.
