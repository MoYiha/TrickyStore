use std::ffi::{c_int, c_void};

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
pub struct InjectorSymbols {
    pub libc_return: usize,
    pub close: usize,
    pub socket: usize,
    pub bind: usize,
    pub recvmsg: usize,
    pub mmap: usize,
    pub munmap: usize,
    pub errno_location: usize,
    pub android_dlopen_ext: usize,
    pub dlerror: usize,
    pub strlen: usize,
    pub dlsym: usize,
    pub dlclose: usize,
}

#[derive(Clone, Copy)]
#[repr(C)]
pub struct SockAddrUn {
    pub family: u16,
    pub path: [u8; 108],
}

impl Default for SockAddrUn {
    fn default() -> Self {
        Self {
            family: 0,
            path: [0; 108],
        }
    }
}

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
pub struct IoVector {
    pub base: *mut c_void,
    pub length: usize,
}

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
pub struct MessageHeader {
    pub name: *mut c_void,
    pub name_length: u32,
    pub vectors: *mut IoVector,
    pub vector_count: usize,
    pub control: *mut c_void,
    pub control_length: usize,
    pub flags: c_int,
}

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
pub struct AndroidDlExtInfo {
    pub flags: u64,
    pub reserved_address: *mut c_void,
    pub reserved_size: usize,
    pub relro_fd: c_int,
    pub library_fd: c_int,
    pub library_fd_offset: i64,
    pub library_namespace: *mut c_void,
}

#[cfg(target_os = "android")]
extern "C" {
    pub fn socket(domain: c_int, kind: c_int, protocol: c_int) -> c_int;
    pub fn sendmsg(descriptor: c_int, message: *const MessageHeader, flags: c_int) -> isize;
    pub fn close(descriptor: c_int) -> c_int;
    pub fn getpid() -> c_int;
    pub fn nice(increment: c_int) -> c_int;
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::mem;

    #[test]
    fn platform_structures_match_supported_64_bit_android_abis() {
        let symbols = InjectorSymbols::default();
        assert_eq!(symbols.libc_return, 0);
        assert_eq!(mem::size_of::<usize>(), 8);
        assert_eq!(mem::size_of::<InjectorSymbols>(), 104);
        assert_eq!(mem::size_of::<SockAddrUn>(), 110);
        assert_eq!(mem::size_of::<IoVector>(), 16);
        assert_eq!(mem::size_of::<MessageHeader>(), 56);
        assert_eq!(mem::offset_of!(MessageHeader, name_length), 8);
        assert_eq!(mem::offset_of!(MessageHeader, vectors), 16);
        assert_eq!(mem::offset_of!(MessageHeader, control), 32);
        assert_eq!(mem::offset_of!(MessageHeader, flags), 48);
        assert_eq!(mem::size_of::<AndroidDlExtInfo>(), 48);
        assert_eq!(mem::offset_of!(AndroidDlExtInfo, relro_fd), 24);
        assert_eq!(mem::offset_of!(AndroidDlExtInfo, library_fd_offset), 32);
        assert_eq!(mem::offset_of!(AndroidDlExtInfo, library_namespace), 40);
    }
}
