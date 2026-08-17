// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use std::io;
use std::mem;
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::os::unix::net::UnixStream;

const FD_MARKER: u8 = 0x46;
const MAX_RECEIVED_FDS: usize = 4;

/// Send exactly one borrowed descriptor. Sender ownership is unchanged.
pub fn send_one_fd(stream: &UnixStream, fd: RawFd) -> io::Result<()> {
    if fd < 0 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "cannot send an invalid descriptor",
        ));
    }

    let mut marker = FD_MARKER;
    let mut iov = libc::iovec {
        iov_base: (&mut marker as *mut u8).cast(),
        iov_len: 1,
    };
    let mut control = vec![0u8; cmsg_space(mem::size_of::<RawFd>())?];
    // SAFETY: zero initializes an empty msghdr. All pointers passed to sendmsg are replaced below
    // with live stack/Vec storage that outlives the syscall.
    let mut message: libc::msghdr = unsafe { mem::zeroed() };
    message.msg_iov = &mut iov;
    message.msg_iovlen = 1;
    message.msg_control = control.as_mut_ptr().cast();
    message.msg_controllen = control.len();

    // SAFETY: control is sized with CMSG_SPACE for one RawFd. CMSG_FIRSTHDR/CMSG_DATA therefore
    // refer inside that live allocation. The kernel copies the descriptor reference during sendmsg
    // and retains neither userspace pointers nor ownership of the sender's file object.
    unsafe {
        let header = libc::CMSG_FIRSTHDR(&message);
        if header.is_null() {
            return Err(io::Error::other("SCM_RIGHTS control buffer is unavailable"));
        }
        (*header).cmsg_level = libc::SOL_SOCKET;
        (*header).cmsg_type = libc::SCM_RIGHTS;
        (*header).cmsg_len = libc::CMSG_LEN(mem::size_of::<RawFd>() as _) as usize;
        (libc::CMSG_DATA(header) as *mut RawFd).write(fd);
    }

    loop {
        // SAFETY: message points only to live storage above. MSG_NOSIGNAL converts a closed peer
        // into the ordinary EPIPE result instead of delivering SIGPIPE to this critical process.
        let sent = unsafe { libc::sendmsg(stream.as_raw_fd(), &message, libc::MSG_NOSIGNAL) };
        if sent == 1 {
            return Ok(());
        }
        if sent >= 0 {
            return Err(io::Error::new(
                io::ErrorKind::WriteZero,
                "SCM_RIGHTS marker was not written atomically",
            ));
        }
        let error = io::Error::last_os_error();
        if error.kind() != io::ErrorKind::Interrupted {
            return Err(error);
        }
    }
}

/// Receive exactly one descriptor and atomically install FD_CLOEXEC.
///
/// Any ambiguous or malformed ancillary message is rejected. Every SCM_RIGHTS descriptor that the
/// kernel installed is closed before returning an error, so invalid messages cannot leak file
/// descriptors into the long-lived service.
pub fn receive_one_fd(stream: &UnixStream) -> io::Result<OwnedFd> {
    let mut marker = 0u8;
    let mut iov = libc::iovec {
        iov_base: (&mut marker as *mut u8).cast(),
        iov_len: 1,
    };
    let mut control = vec![0u8; cmsg_space(MAX_RECEIVED_FDS * mem::size_of::<RawFd>())?];
    // SAFETY: zero initializes an empty msghdr. recvmsg sees only live writable buffers installed
    // below, and the structure is not used after those buffers are dropped.
    let mut message: libc::msghdr = unsafe { mem::zeroed() };
    message.msg_iov = &mut iov;
    message.msg_iovlen = 1;
    message.msg_control = control.as_mut_ptr().cast();
    message.msg_controllen = control.len();

    let received = loop {
        // SAFETY: message references writable marker/control storage. MSG_CMSG_CLOEXEC makes every
        // received descriptor close-on-exec at installation time, avoiding the fcntl race.
        let result =
            unsafe { libc::recvmsg(stream.as_raw_fd(), &mut message, libc::MSG_CMSG_CLOEXEC) };
        if result >= 0 {
            break result;
        }
        let error = io::Error::last_os_error();
        if error.kind() != io::ErrorKind::Interrupted {
            return Err(error);
        }
    };

    let (mut descriptors, ancillary_valid) = collect_rights_fds(&message);
    let flags_invalid = message.msg_flags & (libc::MSG_CTRUNC | libc::MSG_TRUNC) != 0;
    let valid = received == 1
        && marker == FD_MARKER
        && !flags_invalid
        && ancillary_valid
        && descriptors.len() == 1;
    if !valid {
        close_all(&mut descriptors);
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "invalid SCM_RIGHTS message",
        ));
    }

    let raw = descriptors.pop().expect("validated one descriptor");
    // SAFETY: recvmsg installed raw in this process; it has not been wrapped or closed, and the
    // validation above accepted exactly one descriptor. OwnedFd becomes its sole Rust owner.
    Ok(unsafe { OwnedFd::from_raw_fd(raw) })
}

fn collect_rights_fds(message: &libc::msghdr) -> (Vec<RawFd>, bool) {
    let mut descriptors = Vec::new();
    let mut valid = true;
    // SAFETY: message came directly from recvmsg and still points to the live control buffer. The
    // CMSG helpers only walk records inside msg_controllen.
    let mut header = unsafe { libc::CMSG_FIRSTHDR(message) };
    while !header.is_null() {
        // SAFETY: CMSG_FIRSTHDR/CMSG_NXTHDR return headers within the receive buffer or null.
        let current = unsafe { &*header };
        if current.cmsg_level == libc::SOL_SOCKET && current.cmsg_type == libc::SCM_RIGHTS {
            let base_len = match cmsg_len(0) {
                Ok(value) => value,
                Err(_) => return (descriptors, false),
            };
            if current.cmsg_len < base_len {
                valid = false;
            } else {
                let data_len = current.cmsg_len - base_len;
                if data_len == 0 || data_len % mem::size_of::<RawFd>() != 0 {
                    valid = false;
                } else {
                    let count = data_len / mem::size_of::<RawFd>();
                    if count > MAX_RECEIVED_FDS.saturating_sub(descriptors.len()) {
                        valid = false;
                    } else {
                        // SAFETY: data_len is a checked multiple of RawFd and the ancillary payload
                        // was written by the kernel. index is bounded by that payload length.
                        let data = unsafe { libc::CMSG_DATA(header) as *const RawFd };
                        for index in 0..count {
                            // SAFETY: index < count and count was derived from this record's length.
                            descriptors.push(unsafe { data.add(index).read() });
                        }
                    }
                }
            }
        } else {
            // Do not return early: a later SCM_RIGHTS record may already have installed descriptors
            // that must be collected and closed before this message can be rejected safely.
            valid = false;
        }

        // SAFETY: current header belongs to message; helper returns the next in-buffer record or null.
        header = unsafe { libc::CMSG_NXTHDR(message, header) };
    }
    (descriptors, valid)
}

fn close_all(descriptors: &mut Vec<RawFd>) {
    descriptors.drain(..).for_each(close_raw);
}

fn cmsg_space(data_len: usize) -> io::Result<usize> {
    let data_len = u32::try_from(data_len)
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "ancillary data is too large"))?;
    // SAFETY: CMSG_SPACE is a pure bounded size calculation with no pointer inputs.
    Ok(unsafe { libc::CMSG_SPACE(data_len) as usize })
}

fn cmsg_len(data_len: usize) -> io::Result<usize> {
    let data_len = u32::try_from(data_len)
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "ancillary data is too large"))?;
    // SAFETY: CMSG_LEN is a pure bounded size calculation with no pointer inputs.
    Ok(unsafe { libc::CMSG_LEN(data_len) as usize })
}

fn close_raw(raw: RawFd) {
    // SAFETY: callers pass only received descriptors that have not been transferred to OwnedFd, or
    // temporary descriptors with no other Rust owner. Cleanup errors cannot be recovered usefully.
    let _ = unsafe { libc::close(raw) };
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use std::io::{Read, Write};

    #[test]
    fn transfers_one_fd_with_cloexec_without_stealing_sender_ownership() {
        let (sender, receiver) = UnixStream::pair().unwrap();
        let mut pipe = [0; 2];
        // SAFETY: pipe is writable storage for two descriptors; pipe2 initializes both on success.
        assert_eq!(
            unsafe { libc::pipe2(pipe.as_mut_ptr(), libc::O_CLOEXEC) },
            0
        );
        // SAFETY: each descriptor is freshly returned by pipe2 and has no Rust owner yet.
        let read_end = unsafe { OwnedFd::from_raw_fd(pipe[0]) };
        // SAFETY: distinct fresh descriptor from the same successful pipe2 call.
        let write_end = unsafe { OwnedFd::from_raw_fd(pipe[1]) };
        let mut writer = File::from(write_end);
        writer.write_all(b"fd-data").unwrap();
        drop(writer);

        send_one_fd(&sender, read_end.as_raw_fd()).unwrap();
        // SAFETY: F_GETFD is pointer-free and read_end is still owned by this process.
        assert!(unsafe { libc::fcntl(read_end.as_raw_fd(), libc::F_GETFD) } >= 0);

        let received = receive_one_fd(&receiver).unwrap();
        // SAFETY: F_GETFD is pointer-free and received is a live descriptor.
        let flags = unsafe { libc::fcntl(received.as_raw_fd(), libc::F_GETFD) };
        assert_ne!(flags & libc::FD_CLOEXEC, 0);
        let mut file = File::from(received);
        let mut output = Vec::new();
        file.read_to_end(&mut output).unwrap();
        assert_eq!(output, b"fd-data");
    }

    #[test]
    fn missing_fd_is_rejected() {
        let (mut sender, receiver) = UnixStream::pair().unwrap();
        sender.write_all(&[FD_MARKER]).unwrap();
        assert_eq!(
            receive_one_fd(&receiver).unwrap_err().kind(),
            io::ErrorKind::InvalidData
        );
    }
}
