use std::fs;
use std::ptr;

/// Interrupt threads blocked in ioctl calls.
///
/// This function iterates through candidate signals (SIGWINCH, SIGURG) to find one
/// that is safe to use (default or ignored handler), installs a temporary handler
/// without SA_RESTART, and sends the signal to any thread currently blocked in `ioctl`.
/// This causes the `ioctl` to return with `EINTR`, allowing our hook (or the kernel)
/// to resume control.
pub fn kick_already_blocked_ioctls() {
    let signals = [libc::SIGWINCH, libc::SIGURG];
    let pid = unsafe { libc::getpid() };

    for &sig in &signals {
        // Check current handler
        let mut old_sa: libc::sigaction = unsafe { std::mem::zeroed() };
        if unsafe { libc::sigaction(sig, ptr::null(), &mut old_sa) } != 0 {
            continue;
        }

        // If handler is not default or ignore, skip.
        // sa_sigaction is a usize in libc crate for Android/Linux.
        // SIG_DFL is 0, SIG_IGN is 1.
        if old_sa.sa_sigaction != libc::SIG_DFL && old_sa.sa_sigaction != libc::SIG_IGN {
            continue;
        }

        // Install dummy handler
        let mut sa: libc::sigaction = unsafe { std::mem::zeroed() };
        sa.sa_sigaction = handler as usize;
        sa.sa_flags = 0; // No SA_RESTART: essential to interrupt blocking syscalls
        unsafe { libc::sigemptyset(&mut sa.sa_mask) };

        if unsafe { libc::sigaction(sig, &sa, &mut old_sa) } != 0 {
            continue;
        }

        // Iterate threads
        if let Ok(entries) = fs::read_dir(format!("/proc/{}/task", pid)) {
            for entry in entries.flatten() {
                if let Ok(file_name) = entry.file_name().into_string() {
                    if file_name.starts_with('.') {
                        continue;
                    }
                    if let Ok(tid) = file_name.parse::<libc::pid_t>() {
                        if tid == unsafe { libc::gettid() } {
                            continue;
                        }

                        // Check syscall
                        let syscall_path = format!("/proc/{}/task/{}/syscall", pid, tid);
                        if let Ok(content) = fs::read_to_string(syscall_path) {
                            if let Some(syscall_nr_str) = content.split_whitespace().next() {
                                if let Ok(nr) = syscall_nr_str.parse::<i64>() {
                                    if nr == libc::SYS_ioctl as i64 {
                                        unsafe {
                                            libc::syscall(libc::SYS_tgkill, pid, tid, sig);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Wait a bit to ensure signal delivery
        unsafe { libc::usleep(1000) };

        // Restore original handler
        unsafe { libc::sigaction(sig, &old_sa, ptr::null_mut()) };
        return; // Success
    }
}

extern "C" fn handler(_sig: libc::c_int) {}
