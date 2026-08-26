# Changelog

## V2.6.4

- **Safer backups and restores:** invalid policies, templates and keyboxes are rejected before activation, and failed restores no longer leave partial configuration behind.
- **More reliable keybox handling:** recovery, cache updates and backend restarts preserve working keybox state more consistently.
- **Clearer reboot feedback:** identity settings stay visibly marked until the required device restart is completed.
- **Stronger protection and checks:** sensitive backup material is cleaned up sooner, degenerate privacy seeds are rejected, encryption dependencies are updated, and native/Rust checks cover integration changes more reliably.
