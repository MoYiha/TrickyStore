# Changelog

## V2.6.7

- **WebUI Stability:** Resolved "policy controls unavailable" errors and IPC timeouts caused by daemon crash loops and orphaned processes.
- **Daemon Lifecycle:** Fixed severe port conflicts (`os error 98`) by writing `daemon.pid` only after successful socket binding, preventing PID file corruption that left zombie daemons untrackable.
- **Adapter Orphan Elimination:** Added `adapter.pid` tracking so the supervisor explicitly kills old Android adapter processes during upgrades, preventing concurrent adapter instances that caused native SIGSEGV crashes.
- **Crash Loop Circuit Breaker:** After 10 rapid adapter failures, the daemon now enters a 120-second cooldown instead of spinning indefinitely, protecting battery and system resources.
- **Missing File Errors:** Eliminated KernelSU/APatch `SuFilePathHandler` 404 errors for `favicon.ico` by embedding a blank data URI favicon in the WebUI HTML.
- **Documentation:** Updated design references from platform-specific terminology to "Modern" across all translations.
- **Development Process:** Strengthened AGENTS.md and SKILL.md rules against log-silencing, lazy workarounds, and covering up defects instead of fixing root causes.
