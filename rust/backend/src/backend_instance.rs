// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_service_core::ipc::PROTOCOL_VERSION;
use std::io;

pub const OP_BACKEND_PING: u16 = 28;
pub const HANDSHAKE_VERSION: u8 = 1;
pub const EPOCH_BYTES: usize = 16;
pub const RESPONSE_BYTES: usize = 1 + 2 + EPOCH_BYTES;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BackendInstance {
    epoch: [u8; EPOCH_BYTES],
}

impl BackendInstance {
    pub fn generate() -> io::Result<Self> {
        let mut epoch = [0u8; EPOCH_BYTES];
        getrandom::getrandom(&mut epoch).map_err(|error| {
            io::Error::other(format!("backend instance entropy unavailable: {error}"))
        })?;
        if epoch.iter().all(|byte| *byte == 0) {
            return Err(io::Error::other("backend instance epoch is invalid"));
        }
        Ok(Self { epoch })
    }

    pub fn encode(self) -> [u8; RESPONSE_BYTES] {
        let mut response = [0u8; RESPONSE_BYTES];
        response[0] = HANDSHAKE_VERSION;
        response[1..3].copy_from_slice(&PROTOCOL_VERSION.to_be_bytes());
        response[3..].copy_from_slice(&self.epoch);
        response
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generated_epoch_is_nonzero_and_wire_is_fixed() {
        let first = BackendInstance::generate().unwrap();
        let second = BackendInstance::generate().unwrap();
        assert_ne!(first, second);
        let response = first.encode();
        assert_eq!(response.len(), RESPONSE_BYTES);
        assert_eq!(response[0], HANDSHAKE_VERSION);
        assert_eq!(u16::from_be_bytes([response[1], response[2]]), PROTOCOL_VERSION);
        assert!(response[3..].iter().any(|byte| *byte != 0));
    }
}
