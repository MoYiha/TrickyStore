// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use crate::keybox_wire::{parse_and_encode, MAX_KEYBOX_XML_BYTES};
use std::fs::File;
use std::io::Read;
use std::os::fd::OwnedFd;
use zeroize::Zeroize;

pub fn parse_received_fd(fd: OwnedFd) -> Result<Vec<u8>, &'static str> {
    let mut file = File::from(fd);
    let metadata = file.metadata().map_err(|_| "keybox descriptor rejected")?;
    if !metadata.is_file() || !descriptor_size_is_valid(metadata.len()) {
        return Err("keybox descriptor rejected");
    }
    let size = usize::try_from(metadata.len()).map_err(|_| "keybox descriptor rejected")?;

    let mut bytes = Vec::new();
    bytes
        .try_reserve_exact(size)
        .map_err(|_| "keybox allocation failed")?;
    bytes.resize(size, 0);
    if file.read_exact(&mut bytes).is_err() {
        bytes.zeroize();
        return Err("keybox descriptor rejected");
    }
    let mut trailing = [0u8; 1];
    match file.read(&mut trailing) {
        Ok(0) => parse_and_encode(bytes),
        Ok(_) | Err(_) => {
            bytes.zeroize();
            Err("keybox descriptor rejected")
        }
    }
}

fn descriptor_size_is_valid(size: u64) -> bool {
    size != 0 && size <= MAX_KEYBOX_XML_BYTES as u64
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use std::os::fd::{FromRawFd, IntoRawFd};
    use std::sync::atomic::{AtomicU64, Ordering};

    const VALID_EC: &[u8] =
        include_bytes!("../../../service/src/test/resources/keybox/valid_ec.xml");

    struct TestFile {
        path: std::path::PathBuf,
    }

    impl TestFile {
        fn new(content: &[u8]) -> Self {
            static COUNTER: AtomicU64 = AtomicU64::new(1);
            let path = std::env::temp_dir().join(format!(
                "ct-keybox-fd-{}-{}",
                std::process::id(),
                COUNTER.fetch_add(1, Ordering::Relaxed)
            ));
            fs::write(&path, content).unwrap();
            Self { path }
        }
    }

    impl Drop for TestFile {
        fn drop(&mut self) {
            let _ = fs::remove_file(&self.path);
        }
    }

    #[test]
    fn regular_bounded_descriptor_uses_the_same_keybox_encoder() {
        let test = TestFile::new(VALID_EC);
        let file = File::open(&test.path).unwrap();
        let raw = file.into_raw_fd();
        // SAFETY: into_raw_fd transferred the only File ownership into raw and no owner remains.
        let fd = unsafe { OwnedFd::from_raw_fd(raw) };
        let response = parse_received_fd(fd).unwrap();
        assert_eq!(response[0], 3);
        assert_eq!(response[1], 1);
        assert_eq!(response[2], 1);
    }

    #[test]
    fn non_regular_descriptor_is_rejected() {
        let mut pipe = [0; 2];
        // SAFETY: pipe2 writes two fresh descriptors to the provided array on success.
        assert_eq!(
            unsafe { libc::pipe2(pipe.as_mut_ptr(), libc::O_CLOEXEC) },
            0
        );
        // SAFETY: each successful pipe2 descriptor is fresh and currently unowned by Rust.
        let read_end = unsafe { OwnedFd::from_raw_fd(pipe[0]) };
        // SAFETY: close the distinct write descriptor exactly once; no Rust owner exists for it.
        let _ = unsafe { libc::close(pipe[1]) };
        assert_eq!(
            parse_received_fd(read_end),
            Err("keybox descriptor rejected")
        );
    }

    #[test]
    fn empty_and_oversized_descriptors_are_rejected_by_metadata_bound() {
        let test = TestFile::new(b"");
        let file = File::open(&test.path).unwrap();
        let raw = file.into_raw_fd();
        // SAFETY: into_raw_fd transferred unique ownership into raw.
        let fd = unsafe { OwnedFd::from_raw_fd(raw) };
        assert_eq!(parse_received_fd(fd), Err("keybox descriptor rejected"));
        assert!(!descriptor_size_is_valid(0));
        assert!(descriptor_size_is_valid(MAX_KEYBOX_XML_BYTES as u64));
        assert!(!descriptor_size_is_valid(MAX_KEYBOX_XML_BYTES as u64 + 1));
    }
}
