use std::ffi::c_int;

#[cfg(target_os = "android")]
use std::ffi::{c_char, CString};
#[cfg(target_os = "android")]
use std::fs::OpenOptions;
#[cfg(target_os = "android")]
use std::io::Write;
#[cfg(target_os = "android")]
use std::os::unix::fs::OpenOptionsExt;

const MAXIMUM_LOG_BYTES: usize = 2_048;

/// WARN and above are mirrored into the persistent runtime log the WebUI and
/// diagnostics read; lower priorities stay logcat-only to keep the file small.
const LOG_WARN: c_int = 5;
/// The service trims the runtime log past 512 KiB; stop appending once the
/// same bound is reached so the injector can never grow it without limit.
#[cfg(target_os = "android")]
const MAXIMUM_RUNTIME_LOG_BYTES: u64 = 512 * 1024;

#[cfg(target_os = "android")]
const LOG_TAG: &[u8] = b"CleveresTricky\0";

#[cfg(target_os = "android")]
#[link(name = "log")]
extern "C" {
    fn __android_log_write(priority: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

fn bounded_message(message: &str) -> Option<Vec<u8>> {
    let source = message.as_bytes();
    if source.is_empty() {
        return None;
    }
    let mut bounded = Vec::with_capacity(source.len().min(MAXIMUM_LOG_BYTES));
    bounded.extend(source.iter().take(MAXIMUM_LOG_BYTES).map(|byte| {
        if *byte == 0 || *byte == b'\n' || *byte == b'\r' {
            b' '
        } else {
            *byte
        }
    }));
    Some(bounded)
}

/// Formats the runtime-log line for a priority, or None when the priority is
/// below the file threshold. The prefix identifies injector-written lines
/// inside the mixed service log.
fn runtime_log_line(priority: c_int, message: &str) -> Option<Vec<u8>> {
    if priority < LOG_WARN {
        return None;
    }
    let bounded = bounded_message(message)?;
    let mut line = Vec::with_capacity(bounded.len() + 10);
    line.extend_from_slice(b"inject: ");
    line.extend_from_slice(&bounded);
    line.push(b'\n');
    Some(line)
}

#[cfg(target_os = "android")]
fn append_to_runtime_log(priority: c_int, message: &str) {
    let Some(line) = runtime_log_line(priority, message) else {
        return;
    };
    let path = std::path::Path::new(crate::health::STATUS_DIRECTORY).join("native_runtime.log");
    // Refuse a planted symlink and keep the file owner-only; the log is
    // operator-visible and must never redirect a root write elsewhere.
    if let Ok(metadata) = std::fs::symlink_metadata(&path) {
        if metadata.file_type().is_symlink() || !metadata.is_file() {
            return;
        }
        if metadata.len() >= MAXIMUM_RUNTIME_LOG_BYTES {
            return;
        }
    }
    let Ok(mut file) = OpenOptions::new()
        .create(true)
        .append(true)
        .mode(0o600)
        .open(&path)
    else {
        return;
    };
    let _ = file.write_all(&line);
}

#[cfg(target_os = "android")]
pub(crate) fn write(priority: c_int, message: impl AsRef<str>) {
    let message = message.as_ref();
    let source = message.as_bytes();
    let mut bounded = Vec::with_capacity(source.len().min(MAXIMUM_LOG_BYTES));
    bounded.extend(source.iter().take(MAXIMUM_LOG_BYTES).map(|byte| {
        if *byte == 0 {
            b'?'
        } else {
            *byte
        }
    }));
    if let Ok(value) = CString::new(bounded) {
        unsafe {
            __android_log_write(priority, LOG_TAG.as_ptr().cast::<c_char>(), value.as_ptr());
        }
    }
    #[cfg(target_os = "android")]
    append_to_runtime_log(priority, message);
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn warn_and_error_lines_are_mirrored_with_prefix() {
        assert_eq!(
            runtime_log_line(LOG_WARN, "entry stage: rejected").as_deref(),
            Some(b"inject: entry stage: rejected\n".as_slice()),
        );
        assert_eq!(
            runtime_log_line(6, "attach stage: failed"),
            Some(b"inject: attach stage: failed\n".to_vec())
        );
    }

    #[test]
    fn below_warn_priorities_are_not_mirrored() {
        assert_eq!(runtime_log_line(4, "starting validated injection"), None);
        assert_eq!(runtime_log_line(3, "remote library loaded"), None);
    }

    #[test]
    fn mirrored_lines_are_bounded_and_single_line() {
        let long = "x".repeat(MAXIMUM_LOG_BYTES * 2);
        let line = runtime_log_line(6, &long).expect("error lines must be mirrored");
        assert_eq!(line.len(), "inject: ".len() + MAXIMUM_LOG_BYTES + 1);
        assert_eq!(line.last(), Some(&b'\n'));

        let multiline =
            runtime_log_line(5, "first\nsecond\rthird").expect("warn lines must be mirrored");
        assert_eq!(multiline.iter().filter(|byte| **byte == b'\n').count(), 1);
        assert_eq!(multiline, b"inject: first second third\n".to_vec());
    }
}
