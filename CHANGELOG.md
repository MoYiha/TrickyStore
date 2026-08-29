# Changelog

## V2.6.7

- **Memory & Resource Optimization:** Streamlined package enumeration directly from process output streams without buffering large byte arrays in memory, significantly eliminating ART GC pressure and reducing service RAM usage.
- **IPC Resilience & Keybox Updates:** Added automatic reconnect and retry tolerance for native backend socket communication, preventing transient `Broken pipe` errors and keybox update failures during daemon restarts.
- **Daemon Lifecycle & Supervisor Cleanup:** Fixed supervisor retry handling to immediately exit cleanly on code 0, eliminating redundant retry loops and log spam when an active daemon is running. Port conflicts and abstract socket collisions are prevented via strict singleton process tracking.
- **WebUI Performance & Stability:** Optimized log viewer rendering and line collapsing with single-pass string processing, reducing WebView memory consumption and resolving "policy controls unavailable" freezes.
- **Auto Identity Preview Tracking:** Enhanced Android developer portal crawling to prioritize developer preview releases and newer Android preview tracks for automatic Pixel identity generation.
- **WebUI Asset Fix:** Eliminated KernelSU/APatch 404 errors for `favicon.ico` by embedding a data URI favicon directly in the WebUI.
- **Crash Loop Circuit Breaker:** After 10 rapid adapter failures, the daemon enters a cooldown period instead of spinning indefinitely, saving battery and system resources.
