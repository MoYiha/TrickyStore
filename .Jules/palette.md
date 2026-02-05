## 2025-02-05 - Embedded Web Server Testing
**Learning:** For Android apps with embedded `NanoHTTPD` web servers, you can unit test the HTML generation by instantiating the server class (mocking Android dependencies if needed) and extracting the response body from `serve(session)`. This allows for frontend verification using Playwright on the dumped HTML file without needing an emulator.
**Action:** Use `WebServerHtmlTest` pattern to dump HTML to `/tmp/index.html` for Playwright verification in future tasks.
