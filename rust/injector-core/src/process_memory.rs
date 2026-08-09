use crate::abi::IoVector;
use std::ffi::{c_int, c_void};

extern "C" {
    fn process_vm_readv(
        pid: c_int,
        local_vectors: *const IoVector,
        local_count: usize,
        remote_vectors: *const IoVector,
        remote_count: usize,
        flags: usize,
    ) -> isize;
    fn process_vm_writev(
        pid: c_int,
        local_vectors: *const IoVector,
        local_count: usize,
        remote_vectors: *const IoVector,
        remote_count: usize,
        flags: usize,
    ) -> isize;
}

pub(crate) fn read_process_memory(pid: i32, remote_address: usize, output: &mut [u8]) -> bool {
    if pid <= 0 || remote_address == 0 || output.is_empty() || output.len() > isize::MAX as usize {
        return false;
    }
    let local = IoVector {
        base: output.as_mut_ptr().cast(),
        length: output.len(),
    };
    let remote = IoVector {
        base: remote_address as *mut c_void,
        length: output.len(),
    };
    unsafe { process_vm_readv(pid, &local, 1, &remote, 1, 0) == output.len() as isize }
}

pub(crate) fn write_process_memory(pid: i32, remote_address: usize, input: &[u8]) -> bool {
    if pid <= 0 || remote_address == 0 || input.is_empty() || input.len() > isize::MAX as usize {
        return false;
    }
    let local = IoVector {
        base: input.as_ptr().cast_mut().cast(),
        length: input.len(),
    };
    let remote = IoVector {
        base: remote_address as *mut c_void,
        length: input.len(),
    };
    unsafe { process_vm_writev(pid, &local, 1, &remote, 1, 0) == input.len() as isize }
}
