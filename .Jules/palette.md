# Palette's Journal

## 2026-02-05 - File Picker for Text Content
**Learning:** Users uploading configuration files (XML/JSON) often have them as files, not clipboard content. Pasting into a textarea is error-prone.
**Action:** Always provide a "Load from File" button that populates the textarea using FileReader, allowing both file selection and manual editing.

## 2026-02-05 - 2-Step Delete Interaction
**Learning:** Native `confirm()` dialogs are safe but disruptive. A 2-step button (State 1: Action, State 2: Confirmation) provides a smoother "delightful" UX for destructive actions in embedded WebUIs.
**Action:** Use the `dataset.state` pattern with a `setTimeout` reset for future destructive actions in vanilla JS interfaces.

## 2026-02-05 - Accessible Custom Notifications
**Learning:** Visual toast notifications ("Dynamic Islands") are invisible to screen readers without `aria-live`. Adding `role="status"` and `aria-live="polite"` makes them accessible without disrupting the user flow.
**Action:** Ensure all custom notification containers have `aria-live` attributes to announce status changes dynamically.
