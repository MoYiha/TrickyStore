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
    let pid = unsafe { libc::getpid() };

    // 1. Identify all threads currently blocked in ioctl
    // Note: We need to check this *before* iterating signals, as signals are tried one by one.
    let mut target_threads = get_threads_in_ioctl(pid);
    if target_threads.is_empty() {
        return;
    }

    // Candidate signals to try. We use a slice to iterate easily.
    let signals = [libc::SIGWINCH, libc::SIGURG];

    for &sig in &signals {
        if target_threads.is_empty() {
            break;
        }

        // Check current handler for this signal.
        // We only want to use a signal if the application hasn't set a custom handler,
        // so we don't disrupt its logic.
        let mut old_sa: libc::sigaction = unsafe { std::mem::zeroed() };
        if unsafe { libc::sigaction(sig, ptr::null(), &mut old_sa) } != 0 {
            continue;
        }

        // Only proceed if the signal is default or ignored.
        // sa_sigaction is a usize in libc crate for Android/Linux.
        // SIG_DFL is 0, SIG_IGN is 1.
        if old_sa.sa_sigaction != libc::SIG_DFL && old_sa.sa_sigaction != libc::SIG_IGN {
            continue;
        }

        // Install dummy handler without SA_RESTART.
        // SA_RESTART would cause the syscall to automatically restart, defeating our purpose.
        let mut sa: libc::sigaction = unsafe { std::mem::zeroed() };
        // Fix for clippy::function_casts_as_integer
        sa.sa_sigaction = handler as *const () as usize;
        sa.sa_flags = 0;
        unsafe { libc::sigemptyset(&mut sa.sa_mask) };

        if unsafe { libc::sigaction(sig, &sa, &mut old_sa) } != 0 {
            continue;
        }

        // Filter threads: only kick those that do NOT block this signal.
        let mut remaining_threads = Vec::new();
        let mut kicked_any = false;

        for &tid in &target_threads {
            // Check if signal is blocked by the thread.
            // Signals are 1-indexed. Bit 0 corresponds to signal 1.
            // So mask bit (sig - 1) corresponds to signal `sig`.
            let blocked_mask = get_thread_blocked_signals(pid, tid).unwrap_or(0);
            let is_blocked = (blocked_mask & (1u64 << (sig - 1))) != 0;

            if !is_blocked {
                // Signal is NOT blocked, send it.
                unsafe {
                    libc::syscall(libc::SYS_tgkill, pid, tid, sig);
                }
                kicked_any = true;
                // Thread is kicked. We don't add it to remaining_threads.
            } else {
                // Signal is blocked, keep this thread for the next signal candidate.
                remaining_threads.push(tid);
            }
        }

        // Update target list for next iteration
        target_threads = remaining_threads;

        // Wait a bit to ensure signal delivery if we sent anything.
        if kicked_any {
            unsafe { libc::usleep(1000) };
        }

        // Restore original handler
        unsafe { libc::sigaction(sig, &old_sa, ptr::null_mut()) };
    }
}

extern "C" fn handler(_sig: libc::c_int) {}

/// Get a list of TIDs for the given PID that are currently blocked in the `ioctl` syscall.
fn get_threads_in_ioctl(pid: libc::pid_t) -> Vec<libc::pid_t> {
    let mut threads = Vec::new();
    let my_tid = unsafe { libc::gettid() };

    // Read /proc/{pid}/task directory to find all threads
    if let Ok(entries) = fs::read_dir(format!("/proc/{}/task", pid)) {
        for entry in entries.flatten() {
            if let Ok(file_name) = entry.file_name().into_string() {
                if file_name.starts_with('.') {
                    continue;
                }
                if let Ok(tid) = file_name.parse::<libc::pid_t>() {
                    // Don't kick ourselves
                    if tid == my_tid {
                        continue;
                    }

                    // Check which syscall the thread is executing
                    let syscall_path = format!("/proc/{}/task/{}/syscall", pid, tid);
                    if let Ok(content) = fs::read_to_string(syscall_path) {
                        if let Some(syscall_nr_str) = content.split_whitespace().next() {
                            if let Ok(nr) = syscall_nr_str.parse::<i64>() {
                                // SYS_ioctl constant varies by platform.
                                // We cast to i64 to ensure comparison works safely.
                                #[allow(clippy::unnecessary_cast)]
                                if nr == libc::SYS_ioctl as i64 {
                                    threads.push(tid);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    threads
}

/// Read the `SigBlk` mask from `/proc/{pid}/task/{tid}/status`.
/// Returns the mask as a u64, or None if reading/parsing fails.
fn get_thread_blocked_signals(pid: libc::pid_t, tid: libc::pid_t) -> Option<u64> {
    let status_path = format!("/proc/{}/task/{}/status", pid, tid);
    let content = fs::read_to_string(status_path).ok()?;

    for line in content.lines() {
        if line.starts_with("SigBlk:") {
            // Format is "SigBlk:\t0000000000000000"
            if let Some(hex_str) = line.split_whitespace().nth(1) {
                return u64::from_str_radix(hex_str, 16).ok();
            }
        }
    }
    None
}
