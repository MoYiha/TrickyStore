## 2024-05-22 - Installation Script UX
**Learning:** For root tools/modules without a GUI, the installation script (`customize.sh`) is the primary user interface. Improving messages there provides high value.
**Action:** Always verify `ui_print` messages in installation scripts to ensure they are helpful and indicate where configuration files are located.

## 2024-05-23 - Async Feedback in Embedded WebUI
**Learning:** Embedded WebUIs often lack native browser feedback. Users may double-submit actions if buttons don't immediately react.
**Action:** Implement a reusable 'runWithState' pattern in JS to immediately disable buttons and show loading state during async operations.

## 2026-02-02 - Custom Tab Component Accessibility
**Learning:** Custom tab implementations using `div` elements are invisible to screen readers and keyboard users unless explicitly managed with ARIA roles (`tablist`, `tab`, `aria-selected`) and `keydown` handlers.
**Action:** Always check custom navigation components for keyboard accessibility (Enter/Space support) and proper ARIA roles.

## 2026-02-03 - Mobile Text Selection
**Learning:** Selecting long technical strings (like fingerprints) inside `div` elements is often frustrating on mobile touch interfaces due to imprecise cursors.
**Action:** Always provide a dedicated "Copy" button for long, non-editable text fields to improve usability.

## 2026-05-21 - Automated Accessibility Verification
**Learning:** This project utilizes unit tests (`WebServerHtmlTest`) to parse and verify the existence of accessibility attributes (`aria-label`, `label for`) in the server-generated HTML.
**Action:** When working on backend-generated UIs, leverage or create similar string-parsing unit tests to enforce accessibility standards in the CI pipeline, rather than relying solely on frontend inspection.
