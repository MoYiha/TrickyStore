use crate::binder_memory::kernel_copy;
use crate::ffi::{validate_mut_slice_args, validate_slice_args};
use crate::layout::{validate_transaction_layout, RustOffsetCacheView};
use std::mem;

const MAX_TRANSACTIONS_PER_CALL: usize = 1_024;
const MAXIMUM_BINDER_STREAM_BYTES: usize = 8 * 1_024 * 1_024;
const MAX_TRANSACTION_PAYLOAD_BYTES: usize = 512;

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
pub struct RustParsedTransaction {
    pub target_ptr: usize,
    pub cookie: usize,
    pub code: u32,
    pub flags: u32,
    pub sender_pid: i32,
    pub sender_euid: u32,
    pub data_size: u64,
    pub data_buffer: usize,
    pub cmd: u32,
    pub raw_ptr: usize,
    pub raw_size: usize,
    pub valid: u8,
}

fn safe_read<T: Copy>(buffer: &[u8], offset: usize) -> Option<T> {
    let end = offset.checked_add(mem::size_of::<T>())?;
    if end > buffer.len() {
        return None;
    }

    let mut value = mem::MaybeUninit::<T>::uninit();
    // SAFETY: Both ranges are valid for `size_of::<T>()` bytes and do not overlap.
    unsafe {
        std::ptr::copy_nonoverlapping(
            buffer.as_ptr().add(offset),
            value.as_mut_ptr().cast::<u8>(),
            mem::size_of::<T>(),
        );
        Some(value.assume_init())
    }
}

#[inline]
fn ioctl_size(command: u32) -> usize {
    ((command >> IOC_SIZE_SHIFT) & 0x3fff) as usize
}

const IOC_NUMBER_BITS: u32 = 8;
const IOC_TYPE_BITS: u32 = 8;
const IOC_SIZE_BITS: u32 = 14;
const IOC_DIRECTION_BITS: u32 = 2;
const IOC_NUMBER_SHIFT: u32 = 0;
const IOC_TYPE_SHIFT: u32 = IOC_NUMBER_SHIFT + IOC_NUMBER_BITS;
const IOC_SIZE_SHIFT: u32 = IOC_TYPE_SHIFT + IOC_TYPE_BITS;
const IOC_DIRECTION_SHIFT: u32 = IOC_SIZE_SHIFT + IOC_SIZE_BITS;
const IOC_READ: u32 = 2;
const BINDER_TYPE: u32 = b'r' as u32;
const TRANSACTION_NUMBER: u32 = 2;
const REPLY_NUMBER: u32 = 3;

const fn ioctl_direction(command: u32) -> u32 {
    (command >> IOC_DIRECTION_SHIFT) & ((1 << IOC_DIRECTION_BITS) - 1)
}

const fn ioctl_type(command: u32) -> u32 {
    (command >> IOC_TYPE_SHIFT) & ((1 << IOC_TYPE_BITS) - 1)
}

const fn ioctl_number(command: u32) -> u32 {
    (command >> IOC_NUMBER_SHIFT) & ((1 << IOC_NUMBER_BITS) - 1)
}

fn is_transaction_command(command: u32) -> bool {
    ioctl_direction(command) == IOC_READ
        && ioctl_type(command) == BINDER_TYPE
        && ioctl_number(command) == TRANSACTION_NUMBER
}

fn is_probe_layout_command(command: u32) -> bool {
    ioctl_direction(command) == IOC_READ
        && ioctl_type(command) == BINDER_TYPE
        && matches!(ioctl_number(command), TRANSACTION_NUMBER | REPLY_NUMBER)
}

pub fn validate_binder_probe(buffer: &[u8], transaction_size: usize) -> bool {
    if buffer.len() < mem::size_of::<u32>()
        || !(40..=MAX_TRANSACTION_PAYLOAD_BYTES).contains(&transaction_size)
    {
        return false;
    }
    let mut position = 0usize;
    let mut matched_layout = false;
    while position + mem::size_of::<u32>() <= buffer.len() {
        let Some(command) = safe_read::<u32>(buffer, position) else {
            return false;
        };
        position += mem::size_of::<u32>();
        let payload_size = ioctl_size(command);
        let Some(end) = position.checked_add(payload_size) else {
            return false;
        };
        if end > buffer.len() {
            return false;
        }
        if is_probe_layout_command(command) {
            if payload_size != transaction_size {
                return false;
            }
            matched_layout = true;
        }
        position = end;
    }
    matched_layout && position == buffer.len()
}

#[no_mangle]
/// Validates that a Binder response contains the expected transaction layout.
///
/// # Safety
/// `buffer_pointer` must be readable for `length` bytes when `length` is not
/// zero. The memory must remain valid for the duration of the call.
pub unsafe extern "C" fn rust_validate_binder_probe(
    buffer_pointer: *const u8,
    length: usize,
    transaction_size: usize,
) -> bool {
    std::panic::catch_unwind(|| {
        let buffer = match unsafe { validate_slice_args(buffer_pointer, length) } {
            Some(value) => value,
            None => return false,
        };
        validate_binder_probe(buffer, transaction_size)
    })
    .unwrap_or(false)
}

/// Parse a Binder driver response stream into a caller-owned output array.
///
/// Only command words and transaction payloads are copied through the kernel
/// validation pipe. Unrelated driver payloads are skipped by their bounded UAPI
/// size, avoiding a full response copy and any stream-sized heap allocation.
///
/// # Safety
/// The Binder buffer must identify an address that the kernel may attempt to
/// read for `consumed` bytes. The remaining pointers must be valid, correctly
/// aligned, non-overlapping for mutable access, and live for the duration of
/// this call. The C++ caller supplies the ABI offsets only after validating
/// them against live Binder traffic.
#[no_mangle]
pub unsafe extern "C" fn rust_parse_binder_stream(
    buffer_pointer: *const u8,
    consumed: usize,
    buffer_size: usize,
    cache_pointer: *const RustOffsetCacheView,
    output_pointer: *mut RustParsedTransaction,
    output_capacity: usize,
    output_count_pointer: *mut usize,
) -> bool {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        // SAFETY: Forwarded from the function contract and bounded above.
        let count_slice = match unsafe { validate_mut_slice_args(output_count_pointer, 1) } {
            Some(value) => value,
            None => return false,
        };
        count_slice[0] = 0;

        if buffer_pointer.is_null()
            || output_capacity > MAX_TRANSACTIONS_PER_CALL
            || consumed == 0
            || consumed > buffer_size
            || consumed > MAXIMUM_BINDER_STREAM_BYTES
        {
            return false;
        }

        // SAFETY: Forwarded from the function contract and bounded above.
        let cache_slice = match unsafe { validate_slice_args(cache_pointer, 1) } {
            Some(value) => value,
            None => return false,
        };
        let cache = &cache_slice[0];
        if !validate_transaction_layout(cache)
            || cache.transaction_data_size > MAX_TRANSACTION_PAYLOAD_BYTES
            || cache.transaction_data_secctx_size > MAX_TRANSACTION_PAYLOAD_BYTES
        {
            return false;
        }

        // SAFETY: Forwarded from the function contract and bounded above.
        let output = match unsafe { validate_mut_slice_args(output_pointer, output_capacity) } {
            Some(value) => value,
            None => return false,
        };

        let buffer_start = buffer_pointer as usize;
        let mut position = 0usize;
        let mut remaining = consumed;
        while remaining >= mem::size_of::<u32>() {
            let Some(command_address) = buffer_start.checked_add(position) else {
                return false;
            };
            let mut command = 0u32;
            if !kernel_copy(
                command_address,
                &mut command as *mut u32 as usize,
                mem::size_of::<u32>(),
            ) {
                return false;
            }
            position += mem::size_of::<u32>();
            remaining -= mem::size_of::<u32>();

            let payload_size = ioctl_size(command);
            if payload_size > remaining {
                return false;
            }

            let known_transaction_size = payload_size == cache.transaction_data_size
                || payload_size == cache.transaction_data_secctx_size;
            if is_transaction_command(command) && !known_transaction_size {
                return false;
            }
            if is_transaction_command(command)
                && known_transaction_size
                && count_slice[0] < output.len()
            {
                let Some(payload_address) = buffer_start.checked_add(position) else {
                    return false;
                };
                let mut transaction_buffer = [0u8; MAX_TRANSACTION_PAYLOAD_BYTES];
                if !kernel_copy(
                    payload_address,
                    transaction_buffer.as_mut_ptr() as usize,
                    payload_size,
                ) {
                    return false;
                }
                let transaction = &transaction_buffer[..payload_size];
                let (
                    Some(target_ptr),
                    Some(cookie),
                    Some(code),
                    Some(flags),
                    Some(sender_pid),
                    Some(sender_euid),
                    Some(data_size),
                    Some(data_buffer),
                ) = (
                    safe_read::<usize>(transaction, cache.target_ptr_offset),
                    safe_read::<usize>(transaction, cache.cookie_offset),
                    safe_read::<u32>(transaction, cache.code_offset),
                    safe_read::<u32>(transaction, cache.flags_offset),
                    safe_read::<i32>(transaction, cache.sender_pid_offset),
                    safe_read::<u32>(transaction, cache.sender_euid_offset),
                    safe_read::<u64>(transaction, cache.data_size_offset),
                    safe_read::<usize>(transaction, cache.data_ptr_offset),
                )
                else {
                    return false;
                };
                output[count_slice[0]] = RustParsedTransaction {
                    target_ptr,
                    cookie,
                    code,
                    flags,
                    sender_pid,
                    sender_euid,
                    data_size,
                    data_buffer,
                    cmd: command,
                    raw_ptr: payload_address,
                    raw_size: payload_size,
                    valid: 1,
                };
                count_slice[0] += 1;
            }

            position += payload_size;
            remaining -= payload_size;
        }

        remaining == 0 && count_slice[0] > 0
    }))
    .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn write_at<T: Copy>(buffer: &mut [u8], offset: usize, value: T) {
        let bytes = unsafe {
            std::slice::from_raw_parts((&value as *const T).cast::<u8>(), mem::size_of::<T>())
        };
        buffer[offset..offset + bytes.len()].copy_from_slice(bytes);
    }

    fn test_cache(payload_size: usize) -> RustOffsetCacheView {
        RustOffsetCacheView {
            target_ptr_offset: 0,
            cookie_offset: 8,
            code_offset: 16,
            flags_offset: 20,
            sender_pid_offset: 24,
            sender_euid_offset: 28,
            data_size_offset: 36,
            data_ptr_offset: 44,
            transaction_data_size: payload_size,
            transaction_data_secctx_size: payload_size,
            bwr_write_size_offset: 0,
            bwr_write_consumed_offset: 8,
            bwr_write_buffer_offset: 16,
            bwr_read_size_offset: 24,
            bwr_read_consumed_offset: 32,
            bwr_read_buffer_offset: 40,
            bwr_total_size: 48,
            valid: 1,
        }
    }

    fn transaction_command(payload_size: usize, number: u32) -> u32 {
        (IOC_READ << IOC_DIRECTION_SHIFT)
            | (payload_size as u32) << IOC_SIZE_SHIFT
            | (BINDER_TYPE << IOC_TYPE_SHIFT)
            | number
    }

    #[test]
    fn parses_a_bounded_transaction() {
        let payload_size = 64usize;
        let command = transaction_command(payload_size, TRANSACTION_NUMBER);
        let mut input = vec![0u8; mem::size_of::<u32>() + payload_size];
        write_at(&mut input, 0, command);
        write_at(&mut input, 4, 0x1234usize);
        write_at(&mut input, 12, 0x5678usize);
        write_at(&mut input, 20, 42u32);
        write_at(&mut input, 24, 1u32);
        write_at(&mut input, 28, 123i32);
        write_at(&mut input, 32, 10_000u32);
        write_at(&mut input, 40, 16u64);
        write_at(&mut input, 48, 0x9abcusize);

        let cache = test_cache(payload_size);
        let mut output = [RustParsedTransaction::default(); 1];
        let mut count = 0usize;

        let parsed = unsafe {
            rust_parse_binder_stream(
                input.as_ptr(),
                input.len(),
                input.len(),
                &cache,
                output.as_mut_ptr(),
                output.len(),
                &mut count,
            )
        };

        assert!(parsed);
        assert_eq!(count, 1);
        assert_eq!(output[0].code, 42);
        assert_eq!(output[0].data_buffer, 0x9abc);
    }

    #[test]
    fn rejects_an_unbounded_output_capacity() {
        let mut count = 99usize;
        let parsed = unsafe {
            rust_parse_binder_stream(
                std::ptr::null(),
                1,
                1,
                std::ptr::null(),
                std::ptr::null_mut(),
                MAX_TRANSACTIONS_PER_CALL + 1,
                &mut count,
            )
        };
        assert!(!parsed);
        assert_eq!(count, 0);
    }

    #[test]
    fn parses_a_transaction_after_a_large_driver_command_without_large_scratch() {
        let payload_size = 64usize;
        let prefix_size = 0x3fffusize;
        let prefix_command = (IOC_READ << IOC_DIRECTION_SHIFT)
            | (prefix_size as u32) << IOC_SIZE_SHIFT
            | (b'x' as u32) << IOC_TYPE_SHIFT
            | 1;
        let transaction_command = transaction_command(payload_size, TRANSACTION_NUMBER);
        let transaction_command_offset = mem::size_of::<u32>() + prefix_size;
        let transaction_offset = transaction_command_offset + mem::size_of::<u32>();
        let mut input = vec![0u8; transaction_offset + payload_size];
        write_at(&mut input, 0, prefix_command);
        write_at(&mut input, transaction_command_offset, transaction_command);
        write_at(&mut input, transaction_offset + 16, 77u32);

        let cache = test_cache(payload_size);
        let mut output = [RustParsedTransaction::default(); 1];
        let mut count = 0usize;

        let parsed = unsafe {
            rust_parse_binder_stream(
                input.as_ptr(),
                input.len(),
                input.len(),
                &cache,
                output.as_mut_ptr(),
                output.len(),
                &mut count,
            )
        };

        assert!(input.len() > 16 * 1_024);
        assert!(parsed);
        assert_eq!(count, 1);
        assert_eq!(output[0].code, 77);
    }

    #[test]
    fn rejects_an_unreadable_stream_without_dereferencing_it() {
        let cache = test_cache(64);
        let mut output = [RustParsedTransaction::default(); 1];
        let mut count = 99usize;
        let parsed = unsafe {
            rust_parse_binder_stream(
                std::ptr::dangling::<u8>(),
                4,
                4,
                &cache,
                output.as_mut_ptr(),
                output.len(),
                &mut count,
            )
        };
        assert!(!parsed);
        assert_eq!(count, 0);
    }

    #[test]
    fn validates_a_live_transaction_probe() {
        let payload_size = 64usize;
        let command = transaction_command(payload_size, REPLY_NUMBER);
        let mut probe = vec![0u8; mem::size_of::<u32>() + payload_size];
        write_at(&mut probe, 0, command);
        assert!(validate_binder_probe(&probe, payload_size));
        assert!(!validate_binder_probe(&probe, payload_size + 8));
    }

    #[test]
    fn rejects_a_probe_with_trailing_partial_command_bytes() {
        let payload_size = 64usize;
        let command = transaction_command(payload_size, REPLY_NUMBER);
        let mut probe = vec![0u8; mem::size_of::<u32>() + payload_size + 1];
        write_at(&mut probe, 0, command);
        assert!(!validate_binder_probe(&probe, payload_size));
    }

    #[test]
    fn rejects_a_probe_when_a_later_layout_command_disagrees() {
        let payload_size = 64usize;
        let first_command = transaction_command(payload_size, REPLY_NUMBER);
        let second_payload_size = payload_size + 8;
        let second_command = transaction_command(second_payload_size, TRANSACTION_NUMBER);
        let first_end = mem::size_of::<u32>() + payload_size;
        let mut probe = vec![0u8; first_end + mem::size_of::<u32>() + second_payload_size];
        write_at(&mut probe, 0, first_command);
        write_at(&mut probe, first_end, second_command);
        assert!(!validate_binder_probe(&probe, payload_size));
    }
}
