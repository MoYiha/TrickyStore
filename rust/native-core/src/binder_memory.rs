use crate::binder_parser::RustParsedTransaction;
use crate::ffi::{validate_mut_slice_args, validate_slice_args};
use crate::injector_support::wipe_bytes;
use crate::layout::{validate_offset_cache, validate_transaction_layout, RustOffsetCacheView};
use std::cell::RefCell;
use std::ffi::{c_int, c_void};
use std::io;
use std::mem;

const O_NONBLOCK: c_int = 0x800;
const O_CLOEXEC: c_int = 0x80000;
const MAXIMUM_BINDER_WRITE_READ_BYTES: usize = 256;
const MAXIMUM_BINDER_READ_BYTES: usize = 8 * 1_024 * 1_024;
const MAXIMUM_TRANSACTION_PAYLOAD_BYTES: usize = 512;
const KERNEL_COPY_ATTEMPT_BYTES: usize = 64 * 1_024;

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
pub struct RustBinderReadSnapshot {
    pub read_size: usize,
    pub read_consumed: usize,
    pub read_buffer: usize,
    pub valid: u8,
}

extern "C" {
    fn pipe2(descriptors: *mut c_int, flags: c_int) -> c_int;
    fn read(descriptor: c_int, output: *mut c_void, length: usize) -> isize;
    fn write(descriptor: c_int, input: *const c_void, length: usize) -> isize;
    fn close(descriptor: c_int) -> c_int;
}

pub(crate) struct KernelCopyPipe {
    read_descriptor: c_int,
    write_descriptor: c_int,
}

impl KernelCopyPipe {
    pub(crate) fn new() -> Option<Self> {
        let mut descriptors = [-1, -1];
        if unsafe { pipe2(descriptors.as_mut_ptr(), O_CLOEXEC | O_NONBLOCK) } != 0 {
            return None;
        }
        Some(Self {
            read_descriptor: descriptors[0],
            write_descriptor: descriptors[1],
        })
    }

    pub(crate) fn copy(&self, source: usize, destination: usize, length: usize) -> bool {
        if source == 0 || destination == 0 || length == 0 || length > MAXIMUM_BINDER_READ_BYTES {
            return false;
        }

        let mut copied = 0usize;
        while copied < length {
            let attempt_length = (length - copied).min(KERNEL_COPY_ATTEMPT_BYTES);
            let source_address = match source.checked_add(copied) {
                Some(value) => value,
                None => return false,
            };
            let transferred = loop {
                let result = unsafe {
                    write(
                        self.write_descriptor,
                        source_address as *const c_void,
                        attempt_length,
                    )
                };
                if result < 0 && io::Error::last_os_error().kind() == io::ErrorKind::Interrupted {
                    continue;
                }
                if result <= 0 || result as usize > attempt_length {
                    return false;
                }
                break result as usize;
            };

            let mut received = 0usize;
            while received < transferred {
                let destination_address = match destination
                    .checked_add(copied)
                    .and_then(|value| value.checked_add(received))
                {
                    Some(value) => value,
                    None => return false,
                };
                let result = unsafe {
                    read(
                        self.read_descriptor,
                        destination_address as *mut c_void,
                        transferred - received,
                    )
                };
                if result < 0 && io::Error::last_os_error().kind() == io::ErrorKind::Interrupted {
                    continue;
                }
                if result <= 0 || result as usize > transferred - received {
                    return false;
                }
                received += result as usize;
            }
            copied += transferred;
        }
        true
    }
}

impl Drop for KernelCopyPipe {
    fn drop(&mut self) {
        if self.read_descriptor >= 0 {
            unsafe { close(self.read_descriptor) };
            self.read_descriptor = -1;
        }
        if self.write_descriptor >= 0 {
            unsafe { close(self.write_descriptor) };
            self.write_descriptor = -1;
        }
    }
}

thread_local! {
    static KERNEL_COPY_PIPE: RefCell<Option<KernelCopyPipe>> = const { RefCell::new(None) };
}

/// Copies memory through a per-thread kernel pipe so invalid user addresses fail
/// without being dereferenced in-process. A failed transfer discards the pipe to
/// guarantee that partially transferred bytes are never reused by a later copy.
pub(crate) fn kernel_copy(source: usize, destination: usize, length: usize) -> bool {
    KERNEL_COPY_PIPE.with(|slot| {
        let Ok(mut slot) = slot.try_borrow_mut() else {
            return KernelCopyPipe::new()
                .map(|pipe| pipe.copy(source, destination, length))
                .unwrap_or(false);
        };
        if slot.is_none() {
            *slot = KernelCopyPipe::new();
        }
        let success = slot
            .as_ref()
            .map(|pipe| pipe.copy(source, destination, length))
            .unwrap_or(false);
        if !success {
            *slot = None;
        }
        success
    })
}

fn read_unaligned<T: Copy>(input: &[u8], offset: usize) -> Option<T> {
    let end = offset.checked_add(mem::size_of::<T>())?;
    if end > input.len() {
        return None;
    }
    Some(unsafe { input.as_ptr().add(offset).cast::<T>().read_unaligned() })
}

fn write_bytes(output: &mut [u8], offset: usize, value: &[u8]) -> bool {
    let Some(end) = offset.checked_add(value.len()) else {
        return false;
    };
    let Some(destination) = output.get_mut(offset..end) else {
        return false;
    };
    destination.copy_from_slice(value);
    true
}

/// Reads the Binder driver exchange structure through a kernel validated copy.
///
/// # Safety
/// `input_pointer` must be an address that the kernel may attempt to read for
/// the validated structure size. The cache and output pointers must each point
/// to one initialized and writable object respectively. All object memory must
/// remain live for the duration of the call.
#[no_mangle]
pub unsafe extern "C" fn rust_read_binder_write_read(
    input_pointer: *const u8,
    cache_pointer: *const RustOffsetCacheView,
    output_pointer: *mut RustBinderReadSnapshot,
) -> bool {
    std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let output = match unsafe { validate_mut_slice_args(output_pointer, 1) } {
            Some(value) => value,
            None => return false,
        };
        output[0] = RustBinderReadSnapshot::default();

        let cache = match unsafe { validate_slice_args(cache_pointer, 1) } {
            Some(value) => &value[0],
            None => return false,
        };
        if input_pointer.is_null()
            || !validate_offset_cache(cache)
            || cache.bwr_total_size > MAXIMUM_BINDER_WRITE_READ_BYTES
        {
            return false;
        }

        let mut local = [0u8; MAXIMUM_BINDER_WRITE_READ_BYTES];
        if !kernel_copy(
            input_pointer as usize,
            local.as_mut_ptr() as usize,
            cache.bwr_total_size,
        ) {
            wipe_bytes(&mut local[..cache.bwr_total_size]);
            return false;
        }
        let structure = &local[..cache.bwr_total_size];
        let parsed = (
            read_unaligned::<usize>(structure, cache.bwr_read_size_offset),
            read_unaligned::<usize>(structure, cache.bwr_read_consumed_offset),
            read_unaligned::<usize>(structure, cache.bwr_read_buffer_offset),
        );
        let (Some(read_size), Some(read_consumed), Some(read_buffer)) = parsed else {
            wipe_bytes(&mut local[..cache.bwr_total_size]);
            return false;
        };
        wipe_bytes(&mut local[..cache.bwr_total_size]);

        output[0] = RustBinderReadSnapshot {
            read_size,
            read_consumed,
            read_buffer,
            valid: 1,
        };
        true
    }))
    .unwrap_or(false)
}

/// Writes the redirected Binder target fields through a reusable kernel validated pipe.
///
/// # Safety
/// `buffer_pointer` must identify the same Binder buffer represented by the
/// parsed transaction. The transaction and cache pointers must each reference
/// one initialized object. All object memory must remain live for the duration
/// of the call.
#[no_mangle]
pub unsafe extern "C" fn rust_write_binder_transaction(
    buffer_pointer: *mut u8,
    consumed: usize,
    transaction_pointer: *const RustParsedTransaction,
    cache_pointer: *const RustOffsetCacheView,
) -> bool {
    std::panic::catch_unwind(|| {
        let transaction = match unsafe { validate_slice_args(transaction_pointer, 1) } {
            Some(value) => &value[0],
            None => return false,
        };
        let cache = match unsafe { validate_slice_args(cache_pointer, 1) } {
            Some(value) => &value[0],
            None => return false,
        };
        if buffer_pointer.is_null()
            || consumed == 0
            || consumed > MAXIMUM_BINDER_READ_BYTES
            || transaction.valid == 0
            || !validate_transaction_layout(cache)
            || (transaction.raw_size != cache.transaction_data_size
                && transaction.raw_size != cache.transaction_data_secctx_size)
        {
            return false;
        }

        let buffer_start = buffer_pointer as usize;
        let Some(buffer_end) = buffer_start.checked_add(consumed) else {
            return false;
        };
        let Some(transaction_end) = transaction.raw_ptr.checked_add(transaction.raw_size) else {
            return false;
        };
        if transaction.raw_ptr < buffer_start || transaction_end > buffer_end {
            return false;
        }

        // Snapshot and rewrite the complete bounded UAPI record. This turns
        // three independent field writes into one coherent copy, preserves
        // every field that the interceptor does not own, and reduces pipe
        // traffic on the Binder hot path.
        let mut replacement = [0u8; MAXIMUM_TRANSACTION_PAYLOAD_BYTES];
        if transaction.raw_size > replacement.len() {
            return false;
        }
        if !kernel_copy(
            transaction.raw_ptr,
            replacement.as_mut_ptr() as usize,
            transaction.raw_size,
        ) {
            wipe_bytes(&mut replacement[..transaction.raw_size]);
            return false;
        }
        let replacement = &mut replacement[..transaction.raw_size];
        if !write_bytes(
            replacement,
            cache.target_ptr_offset,
            &transaction.target_ptr.to_ne_bytes(),
        ) || !write_bytes(
            replacement,
            cache.cookie_offset,
            &transaction.cookie.to_ne_bytes(),
        ) || !write_bytes(
            replacement,
            cache.code_offset,
            &transaction.code.to_ne_bytes(),
        ) {
            wipe_bytes(replacement);
            return false;
        }

        let success = kernel_copy(
            replacement.as_ptr() as usize,
            transaction.raw_ptr,
            replacement.len(),
        );
        wipe_bytes(replacement);
        success
    })
    .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn test_cache(payload_size: usize) -> RustOffsetCacheView {
        RustOffsetCacheView {
            target_ptr_offset: 0,
            cookie_offset: 8,
            code_offset: 16,
            flags_offset: 20,
            sender_pid_offset: 24,
            sender_euid_offset: 28,
            data_size_offset: 40,
            data_ptr_offset: 48,
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

    #[test]
    fn kernel_copy_rejects_invalid_addresses_without_dereferencing_them() {
        let source = [1u8, 2, 3, 4];
        let mut destination = [0u8; 4];
        assert!(kernel_copy(
            source.as_ptr() as usize,
            destination.as_mut_ptr() as usize,
            source.len(),
        ));
        assert_eq!(destination, source);
        assert!(!kernel_copy(1, destination.as_mut_ptr() as usize, 1));
    }

    #[test]
    fn kernel_copy_handles_inputs_larger_than_pipe_capacity() {
        let source = vec![0x5au8; 128 * 1_024];
        let mut destination = vec![0u8; source.len()];
        assert!(kernel_copy(
            source.as_ptr() as usize,
            destination.as_mut_ptr() as usize,
            source.len(),
        ));
        assert_eq!(destination, source);
    }

    #[test]
    fn kernel_copy_recovers_after_a_failed_transfer() {
        let source = [0x33u8; 32];
        let mut destination = [0u8; 32];
        assert!(!kernel_copy(1, destination.as_mut_ptr() as usize, 1));
        assert!(kernel_copy(
            source.as_ptr() as usize,
            destination.as_mut_ptr() as usize,
            source.len(),
        ));
        assert_eq!(destination, source);
    }

    #[test]
    fn transaction_writeback_is_coherent_and_preserves_unowned_fields() {
        let mut raw = [0xa5u8; 64];
        let original = raw;
        let transaction = RustParsedTransaction {
            target_ptr: 0x1122_3344_5566_7788,
            cookie: 0x8877_6655_4433_2211,
            code: 0xdead_beef,
            raw_ptr: raw.as_mut_ptr() as usize,
            raw_size: raw.len(),
            valid: 1,
            ..RustParsedTransaction::default()
        };
        let cache = test_cache(raw.len());

        assert!(unsafe {
            rust_write_binder_transaction(raw.as_mut_ptr(), raw.len(), &transaction, &cache)
        });

        assert_eq!(
            read_unaligned::<usize>(&raw, 0),
            Some(transaction.target_ptr)
        );
        assert_eq!(read_unaligned::<usize>(&raw, 8), Some(transaction.cookie));
        assert_eq!(read_unaligned::<u32>(&raw, 16), Some(transaction.code));
        assert_eq!(&raw[20..], &original[20..]);
    }
}
