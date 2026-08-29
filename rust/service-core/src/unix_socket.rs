// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use std::io;
use std::mem;
use std::os::fd::{AsRawFd, FromRawFd, RawFd};
use std::os::unix::net::{UnixListener, UnixStream};

pub const DAEMON_SOCKET_NAME: &[u8] = b"cleverestrickyd.v1";

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PeerCredentials {
    pub pid: i32,
    pub uid: u32,
    pub gid: u32,
}

pub fn bind_abstract(name: &[u8]) -> io::Result<UnixListener> {
    validate_name(name)?;
    let raw = create_socket()?;
    let (address, length) = abstract_address(name);
    // SAFETY: `address` is fully initialized for `length` bytes, properly aligned, and lives for
    // the duration of the call. `raw` is a live AF_UNIX stream descriptor. `bind` retains neither
    // pointer nor descriptor ownership.
    let result = unsafe {
        libc::bind(
            raw,
            (&address as *const libc::sockaddr_un).cast::<libc::sockaddr>(),
            length,
        )
    };
    if result != 0 {
        let error = io::Error::last_os_error();
        close_raw(raw);
        return Err(error);
    }
    // SAFETY: `raw` is still a live stream socket and `listen` retains no borrowed pointers.
    if unsafe { libc::listen(raw, 16) } != 0 {
        let error = io::Error::last_os_error();
        close_raw(raw);
        return Err(error);
    }
    // SAFETY: `raw` is a uniquely owned live socket descriptor and has not been wrapped elsewhere.
    // `UnixListener` becomes its sole owner and will close it exactly once.
    Ok(unsafe { UnixListener::from_raw_fd(raw) })
}

pub fn connect_abstract(name: &[u8]) -> io::Result<UnixStream> {
    validate_name(name)?;
    let raw = create_socket()?;
    let (address, length) = abstract_address(name);
    // SAFETY: `address` is initialized and live for `length` bytes; `raw` is a live AF_UNIX stream
    // descriptor. `connect` retains neither pointer nor descriptor ownership.
    let result = unsafe {
        libc::connect(
            raw,
            (&address as *const libc::sockaddr_un).cast::<libc::sockaddr>(),
            length,
        )
    };
    if result != 0 {
        let error = io::Error::last_os_error();
        close_raw(raw);
        return Err(error);
    }
    // SAFETY: `raw` is uniquely owned and connected. `UnixStream` assumes sole ownership and will
    // close it exactly once; no aliasing file object exists.
    Ok(unsafe { UnixStream::from_raw_fd(raw) })
}

pub fn peer_credentials(stream: &UnixStream) -> io::Result<PeerCredentials> {
    let mut credentials = mem::MaybeUninit::<libc::ucred>::uninit();
    let mut length = mem::size_of::<libc::ucred>() as libc::socklen_t;
    // SAFETY: `credentials` is writable, correctly aligned storage for one `ucred`, `length` points
    // to a live socklen value, and `stream` owns a live socket descriptor. `getsockopt` initializes
    // the credentials on success and does not retain any pointer.
    let result = unsafe {
        libc::getsockopt(
            stream.as_raw_fd(),
            libc::SOL_SOCKET,
            libc::SO_PEERCRED,
            credentials.as_mut_ptr().cast(),
            &mut length,
        )
    };
    if result != 0 {
        return Err(io::Error::last_os_error());
    }
    if length as usize != mem::size_of::<libc::ucred>() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "unexpected peer credential size",
        ));
    }
    // SAFETY: successful `getsockopt(SO_PEERCRED)` initialized the complete `ucred` object above.
    let credentials = unsafe { credentials.assume_init() };
    Ok(PeerCredentials {
        pid: credentials.pid,
        uid: credentials.uid,
        gid: credentials.gid,
    })
}

pub fn peer_uid(stream: &UnixStream) -> io::Result<u32> {
    Ok(peer_credentials(stream)?.uid)
}

fn validate_name(name: &[u8]) -> io::Result<()> {
    let sun_path_capacity =
        mem::size_of::<libc::sockaddr_un>() - mem::offset_of!(libc::sockaddr_un, sun_path);
    if name.is_empty() || name.len() + 1 > sun_path_capacity || name.contains(&0) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "abstract socket name is invalid",
        ));
    }
    Ok(())
}

fn create_socket() -> io::Result<RawFd> {
    // SAFETY: `socket` receives only constant domain/type/protocol values and returns a new owned
    // descriptor on success. No pointers or shared state are involved.
    let raw = unsafe { libc::socket(libc::AF_UNIX, libc::SOCK_STREAM | libc::SOCK_CLOEXEC, 0) };
    if raw < 0 {
        return Err(io::Error::last_os_error());
    }
    
    // Fallback for older Android kernels that may silently ignore SOCK_CLOEXEC in socket()
    // SAFETY: F_GETFD and F_SETFD are safe, scalar operations on a live descriptor.
    let flags = unsafe { libc::fcntl(raw, libc::F_GETFD) };
    if flags >= 0 {
        unsafe { libc::fcntl(raw, libc::F_SETFD, flags | libc::FD_CLOEXEC) };
    }
    
    Ok(raw)
}

fn abstract_address(name: &[u8]) -> (libc::sockaddr_un, libc::socklen_t) {
    // SAFETY: all-zero bytes are a valid initial representation for `sockaddr_un`; the family and
    // used `sun_path` bytes are explicitly initialized below before the kernel observes the value.
    let mut address: libc::sockaddr_un = unsafe { mem::zeroed() };
    address.sun_family = libc::AF_UNIX as libc::sa_family_t;
    for (destination, source) in address.sun_path[1..].iter_mut().zip(name.iter().copied()) {
        *destination = source as libc::c_char;
    }
    let length = mem::offset_of!(libc::sockaddr_un, sun_path) + 1 + name.len();
    (address, length as libc::socklen_t)
}

fn close_raw(raw: RawFd) {
    // SAFETY: callers invoke this only for a live descriptor that has not been transferred into an
    // owning Rust file/socket object. Failure during cleanup cannot be usefully recovered here.
    let _ = unsafe { libc::close(raw) };
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::sync::atomic::{AtomicU64, Ordering};
    use std::thread;

    #[test]
    fn abstract_socket_round_trip_and_peer_credentials() {
        static COUNTER: AtomicU64 = AtomicU64::new(1);
        let name = format!(
            "ct-test-{}-{}",
            std::process::id(),
            COUNTER.fetch_add(1, Ordering::Relaxed)
        );
        let listener = bind_abstract(name.as_bytes()).unwrap();
        let worker = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            // SAFETY: these credential syscalls have no pointer arguments, retain no state, and are
            // safe to call from this ordinary test thread.
            let current_uid = unsafe { libc::getuid() };
            // SAFETY: see the credential-syscall rationale immediately above.
            let current_gid = unsafe { libc::getgid() };
            let credentials = peer_credentials(&stream).unwrap();
            assert_eq!(credentials.uid, current_uid);
            assert_eq!(credentials.gid, current_gid);
            assert!(credentials.pid > 0);
            let mut input = [0u8; 4];
            stream.read_exact(&mut input).unwrap();
            assert_eq!(&input, b"ping");
            stream.write_all(b"pong").unwrap();
        });
        let mut stream = connect_abstract(name.as_bytes()).unwrap();
        stream.write_all(b"ping").unwrap();
        let mut output = [0u8; 4];
        stream.read_exact(&mut output).unwrap();
        assert_eq!(&output, b"pong");
        worker.join().unwrap();
    }
}
