use crate::binder_parser::RustParsedTransaction;
use crate::ffi::{validate_mut_slice_args, validate_slice_args};
use crate::layout::{validate_offset_cache, validate_transaction_layout, RustOffsetCacheView};
use std::ffi::{c_int, c_void};
use std::io;
use std::mem;

const O_CLOEXEC: c_int = 0x80000;
const MAXIMUM_BINDER_WRITE_READ_BYTES: usize = 256;
const MAXIMUM_BINDER_READ_BYTES: usize = 8 * 1_024 * 1_024;
const KERNEL_COPY_CHUNK_BYTES: usize = 4 * 1_024;

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
        if unsafe { pipe2(descriptors.as_mut_ptr(), O_CLOEXEC) } != 0 {
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
            let chunk_length = (length - copied).min(KERNEL_COPY_CHUNK_BYTES);
            let mut written = 0usize;
            while written < chunk_length {
                let Some(address) = source
                    .checked_add(copied)
                    .and_then(|value| value.checked_add(written))
                else {
                    return false;
                };
                let result = unsafe {
                    write(
                        self.write_descriptor,
                        address as *const c_void,
                        chunk_length - written,
                    )
                };
                if result < 0 && io::Error::last_os_error().kind() == io::ErrorKind::Interrupted {
                    continue;
                }
                if result <= 0 || result as usize > chunk_length - written {
                    return false;
                }
                written += result as usize;
            }

            let mut received = 0usize;
            while received < chunk_length {
                let Some(address) = destination
                    .checked_add(copied)
                    .and_then(|value| value.checked_add(received))
                else {
                    return false;
                };
                let result = unsafe {
                    read(
                        self.read_descriptor,
                        address as *mut c_void,
                        chunk_length - received,
                    )
                };
                if result < 0 && io::Error::last_os_error().kind() == io::ErrorKind::Interrupted {
                    continue;
                }
                if result <= 0 || result as usize > chunk_length - received {
                    return false;
                }
                received += result as usize;
            }
            copied += chunk_length;
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

fn read_unaligned<T: Copy>(input: &[u8], offset: usize) -> Option<T> {
    let end = offset.checked_add(mem::size_of::<T>())?;
    if end > input.len() {
        return None;
    }
    Some(unsafe { input.as_ptr().add(offset).cast::<T>().read_unaligned() })
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

        let pipe = match KernelCopyPipe::new() {
            Some(value) => value,
            None => return false,
        };
        let mut local = [0u8; MAXIMUM_BINDER_WRITE_READ_BYTES];
        if !pipe.copy(
            input_pointer as usize,
            local.as_mut_ptr() as usize,
            cache.bwr_total_size,
        ) {
            return false;
        }
        let structure = &local[..cache.bwr_total_size];
        let Some(read_size) = read_unaligned::<usize>(structure, cache.bwr_read_size_offset) else {
            return false;
        };
        let Some(read_consumed) =
            read_unaligned::<usize>(structure, cache.bwr_read_consumed_offset)
        else {
            return false;
        };
        let Some(read_buffer) = read_unaligned::<usize>(structure, cache.bwr_read_buffer_offset)
        else {
            return false;
        };

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

/// Writes the redirected Binder target fields through one kernel validated pipe.
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

        let pipe = match KernelCopyPipe::new() {
            Some(value) => value,
            None => return false,
        };
        let fields = [
            (
                &transaction.target_ptr as *const usize as usize,
                cache.target_ptr_offset,
                mem::size_of::<usize>(),
            ),
            (
                &transaction.cookie as *const usize as usize,
                cache.cookie_offset,
                mem::size_of::<usize>(),
            ),
            (
                &transaction.code as *const u32 as usize,
                cache.code_offset,
                mem::size_of::<u32>(),
            ),
        ];

        for (source, offset, length) in fields {
            let Some(destination) = transaction.raw_ptr.checked_add(offset) else {
                return false;
            };
            if !pipe.copy(source, destination, length) {
                return false;
            }
        }
        true
    })
    .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn kernel_copy_rejects_invalid_addresses_without_dereferencing_them() {
        let pipe = KernelCopyPipe::new().unwrap();
        let source = [1u8, 2, 3, 4];
        let mut destination = [0u8; 4];
        assert!(pipe.copy(
            source.as_ptr() as usize,
            destination.as_mut_ptr() as usize,
            source.len(),
        ));
        assert_eq!(destination, source);
        assert!(!pipe.copy(1, destination.as_mut_ptr() as usize, 1));
    }

    #[test]
    fn kernel_copy_chunks_inputs_larger_than_pipe_capacity() {
        let pipe = KernelCopyPipe::new().unwrap();
        let source = vec![0x5au8; 128 * 1_024];
        let mut destination = vec![0u8; source.len()];
        assert!(pipe.copy(
            source.as_ptr() as usize,
            destination.as_mut_ptr() as usize,
            source.len(),
        ));
        assert_eq!(destination, source);
    }
}
