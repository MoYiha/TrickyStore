use std::ffi::{c_char, c_int, CString};

const MAXIMUM_LOG_BYTES: usize = 2_048;
const LOG_TAG: &[u8] = b"CleveresTricky\0";

#[link(name = "log")]
extern "C" {
    fn __android_log_write(priority: c_int, tag: *const c_char, text: *const c_char) -> c_int;
}

pub(crate) fn write(priority: c_int, message: impl AsRef<str>) {
    let source = message.as_ref().as_bytes();
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
}
