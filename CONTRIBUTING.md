# Contributing to CleveresTricky

Thank you for your interest in contributing to CleveresTricky!

## Research & Development

When researching or developing for this module, please give priority to **Chinese**, **Russian**, and **English** sources (especially Chinese) for hooking techniques, module development, and detection bypass methods. These communities often share advanced techniques relevant to this project's goals.

## Code Style

-   **Kotlin/Java:** Follow standard Android coding conventions.
-   **Rust:** Use `cargo fmt`.
-   **C/C++:** Follow the existing style in `module/src/main/cpp/`. Use `-Werror -Wall -Wextra`.
-   **WebUI:** Keep HTML/CSS/JS clean and minimal. Test mobile UX.

## Pull Requests

1.  Fork the repository.
2.  Create a feature branch.
3.  Submit a PR with a clear description of changes.


### AI Agent Development Rules

AI agents generating code or pull requests for CleveresTricky MUST strictly follow these principles:
- **No Hardcoding:** Offsets, kernel structures, and static sizes must be resolved dynamically. Implement heuristic probing, BTF integration, or fallback matrices instead of relying on `sizeof()` or fixed offsets that will break in future Android updates.
- **Deep Testing:** Superficial tests are not acceptable. API and Action tests must be deepened to prove code robustness. Write the tests first, then implement the code.
- **Unbreakable Architecture:** Think thoroughly about how Android and kernel updates will impact the code. The module must be designed to adapt and remain immune to changes.
- **International Standard:** Do not take the easy way out. Provide rigorous, robust, and world-class engineering solutions without skipping steps.

## Testing Requirements

Every PR that adds or modifies a feature **must** include tests that verify the feature works correctly. This ensures the CI pipeline catches regressions early.

### What to test

-   **C++ injection/hooking changes:** Add or update source-level pattern tests in `service/src/test/` (e.g., `InjectionCodeSafetyTest.kt`) that validate critical code patterns such as proper `msg_iov` setup, `SCM_RIGHTS` usage, error handling paths, and register backup/restore.
-   **IMEI/identity spoofing changes:** Add or update tests in `SpoofingCodeSafetyTest.kt` that verify all intercepted transaction codes, fallback generation correctness (Luhn checksums, valid prefixes), secure random usage, and death recipient handling.
-   **WebUI changes:** Add or update tests in `WebUiSafetyTest.kt` that verify mobile UX (viewport, media queries, touch targets), accessibility (ARIA roles), keyboard navigation completeness, security (token auth, HTML escaping), and correct JavaScript string escaping in dynamic handlers.
-   **Configuration changes:** Test that config parsing handles edge cases (empty input, malformed lines, missing files).
-   **Shell script changes:** Test retry logic, error handling, and daemon lifecycle (see `DaemonCrashResilienceTest.kt` for examples).
-   **SELinux policy changes:** Test permissions completeness and absence of overly broad rules (see `SepolicyTest.kt`).

### Where to add tests

-   **Unit tests:** `service/src/test/java/cleveres/tricky/cleverestech/`
-   **Instrumented tests:** `service/src/androidTest/java/cleveres/tricky/cleverestech/`
-   Use the helpers in `TestProjectFiles.kt` (`moduleTemplateFile()`, `moduleCppFile()`, `serviceMainFile()`) to locate project files from tests.

### CI workflow integration

All tests run automatically in the CI workflow (`.github/workflows/build.yml`):
-   **Safety checks** validate SELinux policies, shell scripts, Rust code, and module structure.
-   **Unit tests** run via `:service:testDebugUnitTest` and `:module:testDebugUnitTest`.
-   **Instrumented tests** run on emulators across API levels 31-36 via `connectedCheck`.

If your feature cannot be verified by existing test infrastructure, explain in your PR description how you tested it manually, and consider adding a new test category.

### PR checklist

Before submitting, verify:

- [ ] All existing tests pass (`./gradlew testDebugUnitTest`)
- [ ] New tests are added for the feature or fix
- [ ] Tests cover both the happy path and error/edge cases
- [ ] C++ changes compile without warnings (`-Werror -Wall -Wextra`)
- [ ] WebUI changes are tested on mobile viewport (≤600px)
- [ ] No sensitive data (keys, tokens, paths) is hardcoded
