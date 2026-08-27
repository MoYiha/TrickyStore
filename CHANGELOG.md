# Changelog

## V2.6.5

- **Safer and more responsive WebUI:** all WebUI surfaces were audited beyond Logs for mobile layout, accessibility, localized Telegram community messaging, stale async responses, duplicate mutations, and secure DOM rendering.
- **Bounded runtime work:** retry loops, file observers, auto-identity/keybox workers, IPC workers, Binder interception queues, archive readers, and native process supervision remain policy-gated, cancellable, timeout-bounded, and capped against CPU/RAM runaway.

## V2.6.4

- **Safer backups and restores:** invalid policies, templates and keyboxes are rejected before activation, and failed restores no longer leave partial configuration behind.
- **More reliable keybox handling:** recovery, cache updates and backend restarts preserve working keybox state more consistently; temporary boot-time CRL/network delays retry promptly instead of postponing activation for several minutes.
- **Easier keybox uploads:** copied names such as `keybox (1).xml` and `encrypted (1).cbox` are safely stored as `keybox_1.xml` and `encrypted_1.cbox`.
- **Clearer reboot feedback:** identity settings stay visibly marked until the required device restart is completed.
- **More predictable patch settings:** date-formatted security patch rules keep their configured format until runtime resolution, improving compatibility across patch and attestation paths.
- **Stronger protection and checks:** sensitive backup material is cleaned up sooner, degenerate privacy seeds are rejected, encryption dependencies are updated, and native/Rust checks cover integration changes more reliably.
