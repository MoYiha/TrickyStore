// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_service_core::fd_transport::receive_one_fd;
use std::io;
use std::mem;
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::os::unix::net::UnixStream;

#[test]
fn multiple_descriptors_are_rejected() {
    let (sender, receiver) = UnixStream::pair().unwrap();
    let first = open_dev_null();
    let second = open_dev_null();
    send_two_fds(&sender, [first.as_raw_fd(), second.as_raw_fd()]).unwrap();
    assert_eq!(
        receive_one_fd(&receiver).unwrap_err().kind(),
        io::ErrorKind::InvalidData
    );
}

fn open_dev_null() -> OwnedFd {
    let path = b"/dev/null\0";
    // SAFETY: path is NUL-terminated and immutable for the duration of open. On success open returns
    // one fresh descriptor owned by the caller.
    let raw = unsafe { libc::open(path.as_ptr().cast(), libc::O_RDONLY | libc::O_CLOEXEC) };
    assert!(raw >= 0);
    // SAFETY: raw was freshly returned by open and has no other Rust owner.
    unsafe { OwnedFd::from_raw_fd(raw) }
}

fn send_two_fds(stream: &UnixStream, fds: [RawFd; 2]) -> io::Result<()> {
    let mut marker = 0x46u8;
    let mut iov = libc::iovec {
        iov_base: (&mut marker as *mut u8).cast(),
        iov_len: 1,
    };
    let fd_bytes = mem::size_of_val(&fds);
    // SAFETY: CMSG_SPACE is a pure size calculation over the bounded payload length.
    let control_len = unsafe { libc::CMSG_SPACE(fd_bytes as u32) as usize };
    let mut control = vec![0u8; control_len];
    // SAFETY: zero initializes an empty msghdr; live pointers are installed immediately below.
    let mut message: libc::msghdr = unsafe { mem::zeroed() };
    message.msg_iov = &mut iov;
    message.msg_iovlen = 1;
    message.msg_control = control.as_mut_ptr().cast();
    message.msg_controllen = control.len();

    // SAFETY: control was sized with CMSG_SPACE for exactly these two RawFd values. The kernel
    // copies descriptor references and retains no userspace pointer or ownership.
    let sent = unsafe {
        let header = libc::CMSG_FIRSTHDR(&message);
        assert!(!header.is_null());
        (*header).cmsg_level = libc::SOL_SOCKET;
        (*header).cmsg_type = libc::SCM_RIGHTS;
        (*header).cmsg_len = libc::CMSG_LEN(fd_bytes as u32) as usize;
        std::ptr::copy_nonoverlapping(fds.as_ptr().cast::<u8>(), libc::CMSG_DATA(header), fd_bytes);
        libc::sendmsg(stream.as_raw_fd(), &message, libc::MSG_NOSIGNAL)
    };
    if sent == 1 {
        Ok(())
    } else if sent < 0 {
        Err(io::Error::last_os_error())
    } else {
        Err(io::Error::new(
            io::ErrorKind::WriteZero,
            "SCM_RIGHTS marker was not written atomically",
        ))
    }
}
