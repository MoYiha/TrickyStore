## 2024-05-22 - Installation Script UX
**Learning:** For root tools/modules without a GUI, the installation script (`customize.sh`) is the primary user interface. Improving messages there provides high value.
**Action:** Always verify `ui_print` messages in installation scripts to ensure they are helpful and indicate where configuration files are located.

## 2024-05-23 - Async Feedback in Embedded WebUI
**Learning:** Embedded WebUIs often lack native browser feedback. Users may double-submit actions if buttons don't immediately react.
**Action:** Implement a reusable 'runWithState' pattern in JS to immediately disable buttons and show loading state during async operations.
