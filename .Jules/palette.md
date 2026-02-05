# Palette's Journal

## 2026-02-05 - File Picker for Text Content
**Learning:** Users uploading configuration files (XML/JSON) often have them as files, not clipboard content. Pasting into a textarea is error-prone.
**Action:** Always provide a "Load from File" button that populates the textarea using FileReader, allowing both file selection and manual editing.

## 2026-02-05 - 2-Step Delete Interaction
**Learning:** Native `confirm()` dialogs are safe but disruptive. A 2-step button (State 1: Action, State 2: Confirmation) provides a smoother "delightful" UX for destructive actions in embedded WebUIs.
**Action:** Use the `dataset.state` pattern with a `setTimeout` reset for future destructive actions in vanilla JS interfaces.

## 2026-02-05 - Accessible Notifications
**Learning:** Custom "Dynamic Island" or toast notifications are often invisible to screen readers if they only animate opacity.
**Action:** Always use `role="status"` and `aria-live="polite"` on notification containers so updates are announced without shifting focus.

## 2026-05-21 - CSS Grids vs Inputs
**Learning:** When using `display: grid` (e.g., `grid-2`), direct children become grid items. Adding a sibling `<label>` before an `<input>` breaks the layout if they aren't wrapped in a container `div`.
**Action:** Always wrap `label + input` pairs in a `div` when retrofitting accessibility into existing grid-based layouts.
