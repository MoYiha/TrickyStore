# Changelog

## V2.6.6

- **Key Attestation & Keybox Support:** Resolved key attestation and keybox upload failures by synchronizing backend lifecycle with adapter state and eliminating orphan backend socket holding.
- **WebUI Stability:** Resolved "policy controls unavailable" and "runtime starting; retry shortly" freezes caused by supervisor disconnects and orphaned processes.
- **Daemon & Backend Lifecycle:** Fixed port conflicts (`os error 98`) and abstract socket collisions by tracking `daemon.pid`, `adapter.pid`, and `backend.pid`, ensuring strict singleton cleanup across reloads.
- **Process Orphan Elimination:** Added descriptor-level supervisor broker polling to the unprivileged Rust backend so it cleanly terminates the moment the daemon terminates.
- **Crash Loop Circuit Breaker:** After 10 rapid adapter failures, the daemon enters a 120-second cooldown instead of spinning indefinitely, protecting battery and system resources.
- **Missing File Errors:** Eliminated KernelSU/APatch `SuFilePathHandler` 404 errors for `favicon.ico` by embedding a blank data URI favicon in the WebUI HTML.
- **Documentation:** Updated design references from platform-specific terminology to "Modern" across all translations.
- **Development Process:** Strengthened AGENTS.md and SKILL.md rules against log-silencing, lazy workarounds, and covering up defects instead of fixing root causes.
