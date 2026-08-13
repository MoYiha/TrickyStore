use crate::ffi::{validate_mut_slice_args, validate_slice_args};
use std::cell::Cell;
use std::ffi::{c_char, c_int, c_uint};
use std::mem;
use std::sync::RwLock;

const AT_NO_AUTOMOUNT: c_int = 0x800;
const AT_EMPTY_PATH: c_int = 0x1000;
const STATX_INO: c_uint = 0x100;
const BINDER_FD_CACHE_ENTRIES: usize = 64;
const BINDER_FD_FAST_REVALIDATE_HITS: u8 = 31;
const PROC_FD_PREFIX: &[u8] = b"/proc/self/fd/";
const MAXIMUM_DESCRIPTOR_PATH_BYTES: usize = 64;
const MAXIMUM_DESCRIPTOR_TARGET_BYTES: usize = 256;

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
struct StatxTimestamp {
    seconds: i64,
    nanoseconds: u32,
    reserved: i32,
}

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
struct Statx {
    mask: u32,
    block_size: u32,
    attributes: u64,
    link_count: u32,
    owner: u32,
    group: u32,
    mode: u16,
    spare_zero: u16,
    inode: u64,
    size: u64,
    blocks: u64,
    attributes_mask: u64,
    access_time: StatxTimestamp,
    birth_time: StatxTimestamp,
    change_time: StatxTimestamp,
    modification_time: StatxTimestamp,
    device_type_major: u32,
    device_type_minor: u32,
    device_major: u32,
    device_minor: u32,
    mount_identifier: u64,
    direct_io_memory_alignment: u32,
    direct_io_offset_alignment: u32,
    spare: [u64; 12],
}

const _: [(); 16] = [(); mem::size_of::<StatxTimestamp>()];
const _: [(); 256] = [(); mem::size_of::<Statx>()];
const _: [(); 32] = [(); mem::offset_of!(Statx, inode)];
const _: [(); 136] = [(); mem::offset_of!(Statx, device_major)];

#[derive(Clone, Copy)]
struct BinderFdCacheEntry {
    descriptor: i32,
    device: u64,
    inode: u64,
    is_binder: bool,
}

impl BinderFdCacheEntry {
    const EMPTY: Self = Self {
        descriptor: -1,
        device: 0,
        inode: 0,
        is_binder: false,
    };
}

#[derive(Clone, Copy)]
struct BinderFdFastCacheEntry {
    descriptor: i32,
    hits_remaining: u8,
}

impl BinderFdFastCacheEntry {
    const EMPTY: Self = Self {
        descriptor: -1,
        hits_remaining: 0,
    };
}

static BINDER_FD_CACHE: RwLock<[BinderFdCacheEntry; BINDER_FD_CACHE_ENTRIES]> =
    RwLock::new([BinderFdCacheEntry::EMPTY; BINDER_FD_CACHE_ENTRIES]);
static EMPTY_PATH: [c_char; 1] = [0];

thread_local! {
    static BINDER_FD_FAST_CACHE: Cell<BinderFdFastCacheEntry> =
        const { Cell::new(BinderFdFastCacheEntry::EMPTY) };
}

extern "C" {
    fn statx(
        descriptor: c_int,
        path: *const c_char,
        flags: c_int,
        mask: c_uint,
        output: *mut Statx,
    ) -> c_int;
    fn readlink(path: *const c_char, output: *mut c_char, capacity: usize) -> isize;
}

pub fn is_binder_device_path(path: &[u8]) -> bool {
    if path.is_empty() || path.len() > 255 || path.contains(&0) {
        return false;
    }
    let basename = path.rsplit(|byte| *byte == b'/').next().unwrap_or(path);
    matches!(basename, b"binder" | b"vndbinder" | b"hwbinder")
}

fn descriptor_identity(descriptor: i32) -> Option<(u64, u64)> {
    if descriptor < 0 {
        return None;
    }
    let mut metadata = Statx::default();
    if unsafe {
        statx(
            descriptor,
            EMPTY_PATH.as_ptr(),
            AT_EMPTY_PATH | AT_NO_AUTOMOUNT,
            STATX_INO,
            &mut metadata,
        )
    } != 0
        || metadata.mask & STATX_INO == 0
    {
        return None;
    }
    let device = (u64::from(metadata.device_major) << 32) | u64::from(metadata.device_minor);
    Some((device, metadata.inode))
}

fn write_descriptor_path(descriptor: i32, output: &mut [u8]) -> Option<usize> {
    if descriptor < 0 || output.len() < PROC_FD_PREFIX.len() + 2 {
        return None;
    }
    output[..PROC_FD_PREFIX.len()].copy_from_slice(PROC_FD_PREFIX);

    let mut digits = [0u8; 10];
    let mut digit_start = digits.len();
    let mut value = descriptor as u32;
    loop {
        digit_start = digit_start.checked_sub(1)?;
        digits[digit_start] = b'0' + (value % 10) as u8;
        value /= 10;
        if value == 0 {
            break;
        }
    }
    let digit_count = digits.len() - digit_start;
    let path_length = PROC_FD_PREFIX.len().checked_add(digit_count)?;
    if path_length >= output.len() {
        return None;
    }
    output[PROC_FD_PREFIX.len()..path_length].copy_from_slice(&digits[digit_start..]);
    output[path_length] = 0;
    Some(path_length)
}

fn take_fast_binder_fd_hit(descriptor: i32) -> bool {
    BINDER_FD_FAST_CACHE.with(|cache| {
        let mut entry = cache.get();
        if entry.descriptor != descriptor || entry.hits_remaining == 0 {
            return false;
        }
        entry.hits_remaining -= 1;
        cache.set(entry);
        true
    })
}

fn remember_fast_binder_fd(descriptor: i32) {
    BINDER_FD_FAST_CACHE.with(|cache| {
        cache.set(BinderFdFastCacheEntry {
            descriptor,
            hits_remaining: BINDER_FD_FAST_REVALIDATE_HITS,
        });
    });
}

pub fn is_binder_fd(descriptor: i32) -> bool {
    if descriptor < 0 {
        return false;
    }
    if take_fast_binder_fd_hit(descriptor) {
        return true;
    }

    let Some((device, inode)) = descriptor_identity(descriptor) else {
        return false;
    };
    let slot = descriptor as usize % BINDER_FD_CACHE_ENTRIES;
    if let Ok(cache) = BINDER_FD_CACHE.read() {
        let entry = cache[slot];
        if entry.descriptor == descriptor && entry.device == device && entry.inode == inode {
            if entry.is_binder {
                remember_fast_binder_fd(descriptor);
            }
            return entry.is_binder;
        }
    }

    let mut proc_path = [0u8; MAXIMUM_DESCRIPTOR_PATH_BYTES];
    if write_descriptor_path(descriptor, &mut proc_path).is_none() {
        return false;
    }
    let mut target = [0u8; MAXIMUM_DESCRIPTOR_TARGET_BYTES];
    let target_length = unsafe {
        readlink(
            proc_path.as_ptr().cast(),
            target.as_mut_ptr().cast(),
            target.len(),
        )
    };
    if target_length <= 0 || target_length as usize >= target.len() {
        return false;
    }
    if descriptor_identity(descriptor) != Some((device, inode)) {
        return false;
    }
    let is_binder = is_binder_device_path(&target[..target_length as usize]);
    if let Ok(mut cache) = BINDER_FD_CACHE.write() {
        cache[slot] = BinderFdCacheEntry {
            descriptor,
            device,
            inode,
            is_binder,
        };
    }
    if is_binder {
        remember_fast_binder_fd(descriptor);
    }
    is_binder
}

pub fn parse_android_api_level(value: &[u8]) -> Option<i32> {
    if value.is_empty() || value.len() > 3 || !value.iter().all(u8::is_ascii_digit) {
        return None;
    }
    let api = value.iter().try_fold(0i32, |current, digit| {
        current
            .checked_mul(10)?
            .checked_add(i32::from(digit - b'0'))
    })?;
    (31..=37).contains(&api).then_some(api)
}

pub fn parse_kernel_release(value: &[u8]) -> Option<(i32, i32)> {
    if value.is_empty() || value.len() > 255 {
        return None;
    }
    let separator = value.iter().position(|byte| *byte == b'.')?;
    let major = parse_decimal_component(&value[..separator])?;
    let remainder = &value[separator + 1..];
    let minor_length = remainder
        .iter()
        .position(|byte| !byte.is_ascii_digit())
        .unwrap_or(remainder.len());
    let minor = parse_decimal_component(&remainder[..minor_length])?;
    Some((major, minor))
}

fn parse_decimal_component(value: &[u8]) -> Option<i32> {
    if value.is_empty() || value.len() > 9 || !value.iter().all(u8::is_ascii_digit) {
        return None;
    }
    value.iter().try_fold(0i32, |current, digit| {
        current
            .checked_mul(10)?
            .checked_add(i32::from(digit - b'0'))
    })
}

#[no_mangle]
pub extern "C" fn rust_is_binder_fd(descriptor: i32) -> bool {
    std::panic::catch_unwind(|| is_binder_fd(descriptor)).unwrap_or(false)
}

#[no_mangle]
/// Parses a bounded Android API level string.
///
/// # Safety
/// `value_pointer` must be readable for `length` bytes when `length` is not
/// zero. The memory must remain valid for the duration of the call.
pub unsafe extern "C" fn rust_parse_android_api_level(
    value_pointer: *const u8,
    length: usize,
) -> i32 {
    std::panic::catch_unwind(|| {
        let value = match unsafe { validate_slice_args(value_pointer, length) } {
            Some(value) => value,
            None => return 0,
        };
        parse_android_api_level(value).unwrap_or(0)
    })
    .unwrap_or(0)
}

#[no_mangle]
/// Parses the major and minor components of a bounded kernel release string.
///
/// # Safety
/// `value_pointer` must be readable for `length` bytes when `length` is not
/// zero. Both output pointers must each reference one writable `i32`. All
/// pointed to memory must remain valid for the duration of the call.
pub unsafe extern "C" fn rust_parse_kernel_release(
    value_pointer: *const u8,
    length: usize,
    major_pointer: *mut i32,
    minor_pointer: *mut i32,
) -> bool {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let value = match unsafe { validate_slice_args(value_pointer, length) } {
            Some(value) => value,
            None => return false,
        };
        let major = match unsafe { validate_mut_slice_args(major_pointer, 1) } {
            Some(value) => value,
            None => return false,
        };
        let minor = match unsafe { validate_mut_slice_args(minor_pointer, 1) } {
            Some(value) => value,
            None => return false,
        };
        let Some((parsed_major, parsed_minor)) = parse_kernel_release(value) else {
            return false;
        };
        major[0] = parsed_major;
        minor[0] = parsed_minor;
        true
    }))
    .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classifies_only_supported_binder_devices() {
        assert!(is_binder_device_path(b"/dev/binder"));
        assert!(is_binder_device_path(b"/dev/binderfs/hwbinder"));
        assert!(!is_binder_device_path(b"anon_inode:hwbinder"));
        assert!(!is_binder_device_path(b"/dev/binderfs/binder_logs"));
        assert!(!is_binder_device_path(b"/dev/notbinder"));
    }

    #[test]
    fn descriptor_paths_are_bounded_and_null_terminated() {
        let mut output = [0xa5u8; MAXIMUM_DESCRIPTOR_PATH_BYTES];
        let length = write_descriptor_path(i32::MAX, &mut output).unwrap();
        assert_eq!(&output[..length], b"/proc/self/fd/2147483647");
        assert_eq!(output[length], 0);
        assert!(write_descriptor_path(-1, &mut output).is_none());
    }

    #[test]
    fn fast_binder_cache_revalidates_after_bounded_hits() {
        BINDER_FD_FAST_CACHE.with(|cache| cache.set(BinderFdFastCacheEntry::EMPTY));
        remember_fast_binder_fd(123);
        for _ in 0..BINDER_FD_FAST_REVALIDATE_HITS {
            assert!(take_fast_binder_fd_hit(123));
        }
        assert!(!take_fast_binder_fd_hit(123));
        assert!(!take_fast_binder_fd_hit(124));
    }

    #[test]
    fn statx_layout_matches_the_linux_uapi() {
        assert_eq!(mem::size_of::<StatxTimestamp>(), 16);
        assert_eq!(mem::size_of::<Statx>(), 256);
        assert_eq!(mem::offset_of!(Statx, inode), 32);
        assert_eq!(mem::offset_of!(Statx, device_major), 136);
    }

    #[test]
    fn parses_supported_platform_versions() {
        assert_eq!(parse_android_api_level(b"31"), Some(31));
        assert_eq!(parse_android_api_level(b"36"), Some(36));
        assert_eq!(parse_android_api_level(b"37"), Some(37));
        assert_eq!(parse_android_api_level(b"38"), None);
        assert_eq!(parse_kernel_release(b"6.1.75-android14"), Some((6, 1)));
        assert_eq!(parse_kernel_release(b"invalid"), None);
    }
}
