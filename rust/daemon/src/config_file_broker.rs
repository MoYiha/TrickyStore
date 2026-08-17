// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_service_core::secure_fs::TrustedDir;
use std::io::{self, Read};
use std::path::Path;

pub const MAX_FILE_BYTES: usize = 20 * 1024 * 1024;
pub const MAX_RELATIVE_PATH_BYTES: usize = 511;
pub const MAX_REQUEST_BYTES: usize = 1 + 2 + MAX_RELATIVE_PATH_BYTES + MAX_FILE_BYTES;

const CONFIG_PARENT: &str = "/data/adb";
const CONFIG_ROOT_NAME: &str = "cleverestricky";
const KEYBOX_DIRECTORY: &str = "keyboxes";
const ACTION_WRITE: u8 = 0;
const ACTION_MKDIR: u8 = 1;
const ACTION_TOUCH: u8 = 2;
const ACTION_ROOT_VALIDATE: u8 = 3;
const FILE_MODE: u32 = 0o600;
const DIRECTORY_MODE: u32 = 0o700;

pub fn prepare_root() -> io::Result<TrustedDir> {
    let parent = TrustedDir::open(Path::new(CONFIG_PARENT))?;
    prepare_root_from(&parent)
}

fn prepare_root_from(parent: &TrustedDir) -> io::Result<TrustedDir> {
    parent.mkdir_child(CONFIG_ROOT_NAME, DIRECTORY_MODE)
}

pub(crate) fn handle_stream_from<R: Read>(
    root: &TrustedDir,
    reader: &mut R,
    payload_len: usize,
    scratch: &mut [u8],
) -> io::Result<()> {
    if !(3..=MAX_REQUEST_BYTES).contains(&payload_len) {
        return Err(invalid("invalid config file request size"));
    }
    let mut prefix = [0u8; 3];
    reader.read_exact(&mut prefix)?;
    let action = prefix[0];
    let path_len = u16::from_be_bytes([prefix[1], prefix[2]]) as usize;
    if path_len > MAX_RELATIVE_PATH_BYTES || 3 + path_len > payload_len {
        return Err(invalid("invalid config file path length"));
    }
    if action != ACTION_ROOT_VALIDATE && path_len == 0 {
        return Err(invalid("config file path is empty"));
    }
    let mut path_storage = [0u8; MAX_RELATIVE_PATH_BYTES];
    reader.read_exact(&mut path_storage[..path_len])?;
    let body_len = payload_len - 3 - path_len;
    let result = (|| {
        let path = std::str::from_utf8(&path_storage[..path_len])
            .map_err(|_| invalid("config file path is not valid UTF-8"))?;
        match action {
            ACTION_WRITE => {
                if body_len > MAX_FILE_BYTES {
                    return Err(invalid("config file body exceeds bound"));
                }
                atomic_write_relative_from(root, path, reader, body_len, scratch)
            }
            ACTION_MKDIR => {
                if body_len != 0 || path != KEYBOX_DIRECTORY {
                    return Err(invalid("config directory request rejected"));
                }
                root.mkdir_child(KEYBOX_DIRECTORY, DIRECTORY_MODE)
                    .map(|_| ())
            }
            ACTION_TOUCH => {
                if body_len != 0 || path.contains('/') {
                    return Err(invalid("config touch request rejected"));
                }
                validate_component(path)?;
                match root.create_new_file(path, FILE_MODE) {
                    Ok(file) => {
                        drop(file);
                        root.sync()
                    }
                    Err(error) if error.kind() == io::ErrorKind::AlreadyExists => {
                        let (_, size) = root.open_file_bounded(path, 0)?;
                        if size != 0 {
                            return Err(invalid("config flag is not empty"));
                        }
                        Ok(())
                    }
                    Err(error) => Err(error),
                }
            }
            ACTION_ROOT_VALIDATE => {
                if path_len != 0 || body_len != 0 {
                    return Err(invalid("config root capability request rejected"));
                }
                root.sync()
            }
            _ => Err(invalid("unsupported config file action")),
        }
    })();
    path_storage.fill(0);
    scratch.fill(0);
    result
}

#[cfg(test)]
fn handle_from(root: &TrustedDir, request: &[u8]) -> io::Result<()> {
    let mut reader = io::Cursor::new(request);
    let mut scratch = [0u8; 64];
    handle_stream_from(root, &mut reader, request.len(), &mut scratch)
}

fn atomic_write_relative_from<R: Read>(
    root: &TrustedDir,
    path: &str,
    reader: &mut R,
    body_len: usize,
    scratch: &mut [u8],
) -> io::Result<()> {
    if let Some((directory, name)) = path.split_once('/') {
        if directory != KEYBOX_DIRECTORY || name.contains('/') {
            return Err(invalid("config file path depth exceeds bound"));
        }
        validate_component(name)?;
        let child = root.open_child(KEYBOX_DIRECTORY)?;
        child.atomic_write_from(name, reader, body_len, FILE_MODE, scratch)
    } else {
        validate_component(path)?;
        root.atomic_write_from(path, reader, body_len, FILE_MODE, scratch)
    }
}

fn validate_component(value: &str) -> io::Result<()> {
    if value.is_empty()
        || value == "."
        || value == ".."
        || value.len() > 255
        || value.contains('/')
        || value.contains('\0')
    {
        Err(invalid("invalid config file path component"))
    } else {
        Ok(())
    }
}

fn invalid(message: &'static str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::os::unix::fs::{symlink, PermissionsExt};
    use std::sync::atomic::{AtomicU64, Ordering};

    struct TestRoot {
        path: std::path::PathBuf,
    }

    impl TestRoot {
        fn new() -> Self {
            static COUNTER: AtomicU64 = AtomicU64::new(1);
            let path = std::env::temp_dir().join(format!(
                "ct-config-broker-{}-{}",
                std::process::id(),
                COUNTER.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&path).unwrap();
            Self { path }
        }

        fn trusted(&self) -> TrustedDir {
            TrustedDir::open(&self.path).unwrap()
        }
    }

    impl Drop for TestRoot {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.path);
        }
    }

    fn request(action: u8, path: &str, body: &[u8]) -> Vec<u8> {
        let path = path.as_bytes();
        let mut output = Vec::with_capacity(3 + path.len() + body.len());
        output.push(action);
        output.extend_from_slice(&(path.len() as u16).to_be_bytes());
        output.extend_from_slice(path);
        output.extend_from_slice(body);
        output
    }

    #[test]
    fn initializes_exact_root_capability_and_keeps_children_descriptor_relative() {
        let parent = TestRoot::new();
        let parent_capability = parent.trusted();
        let root = prepare_root_from(&parent_capability).unwrap();
        let root_path = parent.path.join(CONFIG_ROOT_NAME);
        assert!(root_path.is_dir());
        assert_eq!(
            fs::metadata(&root_path).unwrap().permissions().mode() & 0o777,
            DIRECTORY_MODE
        );
        handle_from(&root, &request(ACTION_ROOT_VALIDATE, "", b"")).unwrap();

        let moved_root = parent.path.join("moved-root");
        fs::rename(&root_path, &moved_root).unwrap();
        let outside = parent.path.join("outside");
        fs::create_dir(&outside).unwrap();
        symlink(&outside, &root_path).unwrap();

        handle_from(&root, &request(ACTION_WRITE, "settings.json", b"inside")).unwrap();
        assert_eq!(fs::read(moved_root.join("settings.json")).unwrap(), b"inside");
        assert!(!outside.join("settings.json").exists());
        assert!(prepare_root_from(&parent_capability).is_err());
    }

    #[test]
    fn root_capability_action_rejects_paths_and_payloads() {
        let test = TestRoot::new();
        let root = test.trusted();
        assert!(handle_from(&root, &request(ACTION_ROOT_VALIDATE, "child", b"")).is_err());
        assert!(handle_from(&root, &request(ACTION_ROOT_VALIDATE, "", b"x")).is_err());
    }

    #[test]
    fn writes_root_and_keybox_files_atomically_with_private_modes() {
        let test = TestRoot::new();
        let root = test.trusted();
        handle_from(&root, &request(ACTION_WRITE, "target.txt", b"one\n")).unwrap();
        handle_from(&root, &request(ACTION_MKDIR, KEYBOX_DIRECTORY, b"")).unwrap();
        handle_from(
            &root,
            &request(ACTION_WRITE, "keyboxes/device.xml", b"<xml/>"),
        )
        .unwrap();

        assert_eq!(fs::read(test.path.join("target.txt")).unwrap(), b"one\n");
        assert_eq!(
            fs::read(test.path.join(KEYBOX_DIRECTORY).join("device.xml")).unwrap(),
            b"<xml/>"
        );
        assert_eq!(
            fs::metadata(test.path.join("target.txt"))
                .unwrap()
                .permissions()
                .mode()
                & 0o777,
            FILE_MODE
        );
        assert_eq!(
            fs::metadata(test.path.join(KEYBOX_DIRECTORY))
                .unwrap()
                .permissions()
                .mode()
                & 0o777,
            DIRECTORY_MODE
        );
    }

    #[test]
    fn streamed_write_uses_fixed_scratch_and_exact_declared_length() {
        let test = TestRoot::new();
        let root = test.trusted();
        let body = vec![0xa5; 16 * 1024 + 11];
        let payload = request(ACTION_WRITE, "large.bin", &body);
        let mut reader = io::Cursor::new(payload.as_slice());
        let mut scratch = [0u8; 31];
        handle_stream_from(&root, &mut reader, payload.len(), &mut scratch).unwrap();
        assert_eq!(fs::read(test.path.join("large.bin")).unwrap(), body);
        assert!(scratch.iter().all(|byte| *byte == 0));
    }

    #[test]
    fn touch_is_root_only_empty_and_idempotent() {
        let test = TestRoot::new();
        let root = test.trusted();
        let payload = request(ACTION_TOUCH, "spoof_enabled", b"");
        handle_from(&root, &payload).unwrap();
        handle_from(&root, &payload).unwrap();
        assert_eq!(
            fs::metadata(test.path.join("spoof_enabled")).unwrap().len(),
            0
        );
        assert!(handle_from(&root, &request(ACTION_TOUCH, "keyboxes/flag", b"")).is_err());
    }

    #[test]
    fn traversal_depth_symlink_and_unbounded_requests_fail_closed() {
        let test = TestRoot::new();
        fs::create_dir(test.path.join("real-keyboxes")).unwrap();
        symlink(
            test.path.join("real-keyboxes"),
            test.path.join(KEYBOX_DIRECTORY),
        )
        .unwrap();
        let root = test.trusted();

        for path in ["../outside", ".", "keyboxes/../outside", "keyboxes/a/b"] {
            assert!(handle_from(&root, &request(ACTION_WRITE, path, b"x")).is_err());
        }
        assert!(handle_from(&root, &request(ACTION_WRITE, "keyboxes/device.xml", b"x")).is_err());

        let oversized = vec![0u8; MAX_FILE_BYTES + 1];
        assert!(handle_from(&root, &request(ACTION_WRITE, "large.bin", &oversized)).is_err());
    }

    #[test]
    fn replacing_destination_with_symlink_never_writes_through_target() {
        let test = TestRoot::new();
        let outside = test.path.with_extension("outside");
        fs::write(&outside, b"outside").unwrap();
        symlink(&outside, test.path.join("target.txt")).unwrap();
        let root = test.trusted();

        handle_from(&root, &request(ACTION_WRITE, "target.txt", b"inside")).unwrap();
        assert_eq!(fs::read(&outside).unwrap(), b"outside");
        assert_eq!(fs::read(test.path.join("target.txt")).unwrap(), b"inside");
        let _ = fs::remove_file(outside);
    }
}
