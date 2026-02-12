## 2025-02-05 - Embedded Web Server Testing
**Learning:** For Android apps with embedded `NanoHTTPD` web servers, you can unit test the HTML generation by instantiating the server class (mocking Android dependencies if needed) and extracting the response body from `serve(session)`. This allows for frontend verification using Playwright on the dumped HTML file without needing an emulator.
**Action:** Use `WebServerHtmlTest` pattern to dump HTML to `/tmp/index.html` for Playwright verification in future tasks.

## 2026-02-06 - Validating Embedded UI with Playwright
**Learning:** When verifying embedded UIs where the HTML is constructed as a raw string in Kotlin/Java, you can extract the HTML string directly (via regex or simple string parsing) to a standalone file. This file can then be served via a simple HTTP server (e.g., `python3 -m http.server`) to allow Playwright verification of structure and accessibility attributes, bypassing the need for a full Android build environment.
**Action:** Use this extraction method for rapid UI verification of embedded servers.

## 2026-02-08 - Mirrored Static Artifacts Drift
**Learning:** When a project maintains a static file (e.g., `index.html`) as a mirror of an embedded string in code (e.g., `WebServer.kt`), they can easily drift out of sync (e.g., missing CSS rules).
**Action:** Always diff the static artifact against the source of truth before editing to detect and resolve drift, ensuring consistent behavior across dev and prod.

## 2026-02-09 - Async Button State Management
**Learning:** When adding temporary visual feedback to buttons (e.g., "✓ Copied"), rapid clicking can cause race conditions where the temporary text becomes permanent if the "original text" is captured during the temporary state.
**Action:** Always implement a guard clause (e.g., `if (btn.innerText === '✓ Copied') return;`) or use a dedicated state flag/attribute to prevent re-triggering the action while in the temporary feedback state.

## 2026-06-03 - Fingerprint Copy Button
**Learning:** Users who generate random identities often need to copy the resulting fingerprint string for use in other tools or configs, but selecting long text in a mobile browser (embedded UI) is difficult.
**Action:** Added a "Copy" button next to the "Fingerprint" header in the Device Identity preview. Also synchronized `index.html` which had drifted from `WebServer.kt`.

## 2026-06-05 - Accessible Tab Navigation (Roving Tabindex)
**Learning:** Implementing proper ARIA tab navigation requires managing `tabindex` dynamically (roving tabindex). Only the active tab should be focusable (`tabindex="0"`) via the Tab key, while arrow keys should be used to navigate between tabs. Initializing all tabs with `tabindex="0"` forces users to tab through every single tab, which is inefficient.
**Action:** Implemented `handleTabNavigation` for Arrow keys and updated `switchTab` to manage `tabindex` (-1/0) and focus, ensuring a standard accessible tab experience.

## 2026-06-12 - Notification Severity Handling
**Learning:** The existing notification system (`notify`) accepted a severity `type` but visually ignored it unless it was 'working'. This led to indistinguishable feedback for errors vs success.
**Action:** When implementing notification systems, verify that all severity levels (error, warning, success) have distinct visual treatments (color, icon) in addition to accessible attributes.

## 2026-07-20 - Kotlin String Interpolation in Embedded JS
**Learning:** When embedding JavaScript within Kotlin `"""` strings, using template literals (`${var}`) conflicts with Kotlin's string interpolation. You must escape the dollar sign as `${'$'}` in Kotlin to ensure it is emitted literally for the JS runtime.
**Action:** Always use `${'$'}` when writing JS template literals inside Kotlin multiline strings.
