# Changelog

## V2.6.4

- **Safer backups and restores:** invalid policies, templates and keyboxes are rejected before activation, and failed restores no longer leave partial configuration behind.
- **More reliable keybox handling:** recovery, cache updates and backend restarts preserve working keybox state more consistently; temporary boot-time CRL/network delays retry promptly instead of postponing activation for several minutes.
- **Easier keybox uploads:** copied names such as `keybox (1).xml` and `encrypted (1).cbox` are safely stored as `keybox_1.xml` and `encrypted_1.cbox`.
- **Clearer reboot feedback:** identity settings stay visibly marked until the required device restart is completed.
- **Stronger protection and checks:** sensitive backup material is cleaned up sooner, degenerate privacy seeds are rejected, encryption dependencies are updated, and native/Rust checks cover integration changes more reliably.
