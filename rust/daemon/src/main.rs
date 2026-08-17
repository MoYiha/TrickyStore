// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
mod config_file_broker;
mod keybox_file_broker;

use cleverestricky_service_core::ipc::{
    read_header_bounded, relay_exact, write_frame, write_header, FrameHeader, FLAG_ERROR,
    MAX_FRAME_BYTES, OP_ADAPTER_REGISTER, OP_FILE_WRITE, OP_PING, OP_WEB_REQUEST,
    STREAM_COPY_BYTES,
};
use cleverestricky_service_core::unix_socket::{
    bind_abstract, peer_credentials, DAEMON_SOCKET_NAME,
};
use std::env;
use std::fs;
use std::io;
use std::os::fd::{AsRawFd, RawFd};
use std::os::unix::net::UnixStream;
use std::os::unix::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::{self, Child, Command, Stdio};
use std::thread;
use std::time::{Duration, Instant};

const CLIENT_TIMEOUT: Duration = Duration::from_secs(30);
const MAX_ERROR_BYTES: usize = 512;
const BACKEND_RESTART_LIMIT: u32 = 5;
const BACKEND_STABLE_INTERVAL: Duration = Duration::from_secs(5 * 60);
const BACKEND_MAX_BACKOFF: Duration = Duration::from_secs(30);
const BACKEND_BROKER_FD: RawFd = 9;
const MAX_CLIENT_FRAME_BYTES: usize = config_file_broker::MAX_REQUEST_BYTES;

fn main() {
    if let Err(error) = run() {
        eprintln!("cleverestrickyd: {error}");
        process::exit(1);
    }
}

fn run() -> io::Result<()> {
    harden_process()?;
    let module_dir = module_directory()?;
    validate_module_directory(&module_dir)?;
    let listener = bind_abstract(DAEMON_SOCKET_NAME)?;
    let mut adapter = spawn_android_adapter(&module_dir)?;
    let adapter_pid = adapter.id();

    thread::Builder::new()
        .name("ct-ipc".to_string())
        .spawn(move || {
            if let Err(error) = serve(listener, adapter_pid) {
                eprintln!("cleverestrickyd: IPC service failed: {error}");
                process::exit(1);
            }
        })?;

    let backend_dir = module_dir.clone();
    thread::Builder::new()
        .name("ct-backend".to_string())
        .spawn(move || supervise_backend(backend_dir, adapter_pid))?;

    let status = adapter.wait()?;
    eprintln!("cleverestrickyd: Android adapter exited with {status}");
    Err(io::Error::new(
        io::ErrorKind::BrokenPipe,
        "Android adapter exited",
    ))
}

fn module_directory() -> io::Result<PathBuf> {
    if let Some(argument) = env::args_os().nth(1) {
        return Ok(PathBuf::from(argument));
    }
    let executable = env::current_exe()?;
    executable
        .parent()
        .map(Path::to_path_buf)
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "daemon has no module parent"))
}

fn validate_module_directory(module_dir: &Path) -> io::Result<()> {
    let metadata = fs::symlink_metadata(module_dir)?;
    if metadata.file_type().is_symlink() || !metadata.is_dir() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "module directory is unsafe",
        ));
    }
    require_regular_file(&module_dir.join("service.apk"), "service.apk")
}

fn require_regular_file(path: &Path, name: &str) -> io::Result<()> {
    let metadata = fs::symlink_metadata(path)?;
    if metadata.file_type().is_symlink() || !metadata.is_file() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("{name} is unsafe"),
        ));
    }
    Ok(())
}

fn harden_process() -> io::Result<()> {
    // SAFETY: `umask` takes a value argument only, has process-global semantics intended for this
    // single-purpose daemon, and retains no pointers or references.
    unsafe { libc::umask(0o077) };
    // SAFETY: `prctl(PR_SET_DUMPABLE, 0)` has no pointer arguments. This daemon intentionally makes
    // itself non-dumpable before it accepts privileged IPC or starts the Android adapter.
    if unsafe { libc::prctl(libc::PR_SET_DUMPABLE, 0, 0, 0, 0) } != 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}

fn spawn_android_adapter(module_dir: &Path) -> io::Result<Child> {
    let classpath = module_dir.join("service.apk");
    let mut command = Command::new("/system/bin/app_process");
    command
        .arg("/")
        .arg("--nice-name=CleveresTricky")
        .arg("cleveres.tricky.cleverestech.MainKt")
        .env("CLASSPATH", classpath)
        .stdin(Stdio::null())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    // SAFETY: the pre-exec closure uses only async-signal-safe Linux syscalls (`prctl`, `getppid`)
    // and constructs no shared Rust state after fork. It runs in the child immediately before exec.
    // PR_SET_PDEATHSIG prevents an adapter orphan if the Rust supervisor is terminated, while the
    // parent-PID check closes the race where the parent exits between fork and `prctl`.
    unsafe {
        command.pre_exec(|| {
            if libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGTERM, 0, 0, 0) != 0 {
                return Err(io::Error::last_os_error());
            }
            if libc::getppid() == 1 {
                libc::_exit(125);
            }
            Ok(())
        });
    }
    command.spawn()
}

fn spawn_backend(module_dir: &Path, adapter_pid: u32) -> io::Result<(Child, UnixStream)> {
    let path = module_dir.join("cleverestricky_backend");
    require_regular_file(&path, "cleverestricky_backend")?;
    let (daemon_broker, child_broker) = UnixStream::pair()?;
    set_cloexec(daemon_broker.as_raw_fd())?;
    set_cloexec(child_broker.as_raw_fd())?;
    let child_broker_fd = child_broker.as_raw_fd();

    let mut command = Command::new(path);
    command
        .arg(adapter_pid.to_string())
        .env_clear()
        .stdin(Stdio::null())
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());
    // SAFETY: the closure performs only async-signal-safe descriptor syscalls. `child_broker_fd` is
    // live in the forked child and the target descriptor is a fixed value below RLIMIT_NOFILE.
    unsafe {
        command.pre_exec(move || inherit_broker_fd(child_broker_fd));
    }
    let child = command.spawn()?;
    drop(child_broker);
    Ok((child, daemon_broker))
}

fn set_cloexec(fd: RawFd) -> io::Result<()> {
    // SAFETY: F_GETFD/F_SETFD are scalar descriptor operations and retain no pointers.
    let flags = unsafe { libc::fcntl(fd, libc::F_GETFD) };
    if flags < 0 {
        return Err(io::Error::last_os_error());
    }
    // SAFETY: fd remains live for this call; the flags value came from F_GETFD above.
    if unsafe { libc::fcntl(fd, libc::F_SETFD, flags | libc::FD_CLOEXEC) } < 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}

fn inherit_broker_fd(source: RawFd) -> io::Result<()> {
    if source != BACKEND_BROKER_FD {
        // SAFETY: both descriptors are scalar values. dup2 atomically replaces the target and
        // clears close-on-exec on the inherited copy. The source is closed only after success.
        if unsafe { libc::dup2(source, BACKEND_BROKER_FD) } < 0 {
            return Err(io::Error::last_os_error());
        }
        // SAFETY: source remains a distinct live descriptor after successful dup2.
        let _ = unsafe { libc::close(source) };
        return Ok(());
    }

    // SAFETY: when source already equals the fixed target we only clear FD_CLOEXEC so exec keeps it.
    let flags = unsafe { libc::fcntl(source, libc::F_GETFD) };
    if flags < 0 || unsafe { libc::fcntl(source, libc::F_SETFD, flags & !libc::FD_CLOEXEC) } < 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}

fn run_backend_once(module_dir: &Path, adapter_pid: u32) -> io::Result<String> {
    let (mut child, broker) = spawn_backend(module_dir, adapter_pid)?;
    let backend_pid = child.id();
    let broker_thread = match thread::Builder::new()
        .name("ct-keybox-broker".to_string())
        .spawn(move || {
            if let Err(error) = keybox_file_broker::serve(broker) {
                eprintln!("cleverestrickyd: keybox broker failed: {error}");
                // SAFETY: backend_pid came from the live Child. SIGTERM is a scalar process signal
                // used only to force a clean supervised restart after private broker failure.
                let _ = unsafe { libc::kill(backend_pid as libc::pid_t, libc::SIGTERM) };
            }
        }) {
        Ok(handle) => handle,
        Err(error) => {
            let _ = child.kill();
            let _ = child.wait();
            return Err(error);
        }
    };

    let status = child.wait()?;
    broker_thread
        .join()
        .map_err(|_| io::Error::other("keybox broker thread panicked"))?;
    Ok(format!("backend exited with {status}"))
}

fn supervise_backend(module_dir: PathBuf, adapter_pid: u32) {
    let mut rapid_failures = 0u32;
    loop {
        let started = Instant::now();
        let outcome = run_backend_once(&module_dir, adapter_pid);
        match outcome {
            Ok(message) => eprintln!("cleverestrickyd: {message}"),
            Err(error) => eprintln!("cleverestrickyd: backend launch/wait failed: {error}"),
        }

        if started.elapsed() >= BACKEND_STABLE_INTERVAL {
            rapid_failures = 0;
        } else {
            rapid_failures = rapid_failures.saturating_add(1);
        }
        if rapid_failures >= BACKEND_RESTART_LIMIT {
            eprintln!(
                "cleverestrickyd: disabling optional backend after {rapid_failures} rapid failures"
            );
            return;
        }
        let backoff_seconds = 1u64 << rapid_failures.min(5);
        thread::sleep(Duration::from_secs(backoff_seconds).min(BACKEND_MAX_BACKOFF));
    }
}

fn serve(listener: std::os::unix::net::UnixListener, adapter_pid: u32) -> io::Result<()> {
    let mut adapter: Option<std::os::unix::net::UnixStream> = None;
    let mut relay_buffer = vec![0u8; STREAM_COPY_BYTES];
    loop {
        let (mut client, _) = match listener.accept() {
            Ok(value) => value,
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(error),
        };
        let credentials = match peer_credentials(&client) {
            Ok(value) if value.uid == 0 => value,
            Ok(_) => continue,
            Err(_) => continue,
        };
        client.set_read_timeout(Some(CLIENT_TIMEOUT))?;
        client.set_write_timeout(Some(CLIENT_TIMEOUT))?;
        let header = match read_header_bounded(&mut client, MAX_CLIENT_FRAME_BYTES) {
            Ok(value) => value,
            Err(error) => {
                let _ = reply_error(&mut client, OP_PING, &error);
                continue;
            }
        };
        let peer_is_adapter = u32::try_from(credentials.pid)
            .ok()
            .is_some_and(|pid| pid == adapter_pid);

        match header.opcode {
            OP_ADAPTER_REGISTER => {
                if !peer_is_adapter || header.flags != 0 || header.payload_len != 0 {
                    let _ = reply_text_error(
                        &mut client,
                        OP_ADAPTER_REGISTER,
                        "invalid adapter registration",
                    );
                    continue;
                }
                write_frame(&mut client, OP_ADAPTER_REGISTER, 0, b"ok")?;
                adapter = Some(client);
            }
            OP_PING if header.flags == 0 && header.payload_len == 0 => {
                write_frame(&mut client, OP_PING, 0, b"pong")?;
            }
            OP_FILE_WRITE if peer_is_adapter && header.flags == 0 => {
                if header.payload_len > config_file_broker::MAX_REQUEST_BYTES {
                    let _ =
                        reply_text_error(&mut client, OP_FILE_WRITE, "file request exceeds bound");
                    continue;
                }
                match config_file_broker::handle_stream(
                    &mut client,
                    header.payload_len,
                    &mut relay_buffer,
                ) {
                    Ok(()) => write_frame(&mut client, OP_FILE_WRITE, 0, b"ok")?,
                    Err(_) => {
                        let _ =
                            reply_text_error(&mut client, OP_FILE_WRITE, "file operation rejected");
                    }
                }
            }
            OP_WEB_REQUEST if header.flags == 0 && header.payload_len <= MAX_FRAME_BYTES => {
                if let Err(error) =
                    forward_web_request(&mut client, header, &mut adapter, &mut relay_buffer)
                {
                    adapter = None;
                    let _ = reply_error(&mut client, OP_WEB_REQUEST, &error);
                }
            }
            _ => {
                let _ = reply_text_error(&mut client, header.opcode, "unsupported IPC operation");
            }
        }
    }
}

fn forward_web_request(
    client: &mut std::os::unix::net::UnixStream,
    request: FrameHeader,
    adapter: &mut Option<std::os::unix::net::UnixStream>,
    scratch: &mut [u8],
) -> io::Result<()> {
    let target = adapter.as_mut().ok_or_else(|| {
        io::Error::new(
            io::ErrorKind::NotConnected,
            "Android adapter is unavailable",
        )
    })?;
    target.set_read_timeout(Some(CLIENT_TIMEOUT))?;
    target.set_write_timeout(Some(CLIENT_TIMEOUT))?;
    write_header(target, request)?;
    relay_exact(client, target, request.payload_len, scratch)?;

    let response = read_header_bounded(target, MAX_FRAME_BYTES)?;
    if response.opcode != OP_WEB_REQUEST || response.flags != 0 {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "adapter returned an invalid response header",
        ));
    }
    write_header(client, response)?;
    relay_exact(target, client, response.payload_len, scratch)
}

fn reply_error(
    stream: &mut std::os::unix::net::UnixStream,
    opcode: u16,
    error: &io::Error,
) -> io::Result<()> {
    reply_text_error(stream, opcode, &error.to_string())
}

fn reply_text_error(
    stream: &mut std::os::unix::net::UnixStream,
    opcode: u16,
    message: &str,
) -> io::Result<()> {
    let bytes = message.as_bytes();
    write_frame(
        stream,
        opcode.max(1),
        FLAG_ERROR,
        &bytes[..bytes.len().min(MAX_ERROR_BYTES)],
    )
}
