# Changelog

## V2.6.6

- **Refined iOS-inspired WebUI:** Polished typography, form layouts, dynamic island alerts, adaptive mobile navigation tabs, and responsive table views for a cleaner, modern experience.
- **Smooth mobile scrolling and interactions:** Fixed WebView scroll locks and touch handling so all pages scroll naturally and seamlessly on mobile devices.
- **Instant keybox synchronization:** Stored keybox updates synchronize immediately across the system, and keybox deletion operates reliably without spurious error prompts.
- **Clean Donate presentation:** Streamlined Donate tab styling to match system accent colors without distracting shadows or box overlays.
- **WebUI startup readiness:** The interface gracefully waits for backend initialization and retries connections automatically on first launch.
- **Robust background daemon lifecycle:** Fixed port/address conflicts (`os error 98`) via strict supervisor process management and child lifecycle bindings.
- **Stronger resource boundaries:** Polling overhead has been replaced with event-driven coordination, and memory limits are strictly enforced across uploads and staging operations.
