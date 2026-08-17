// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_service_core::ipc::PROTOCOL_VERSION;
use std::io;
use std::sync::OnceLock;
use zeroize::Zeroize;

pub const OP_BACKEND_PING: u16 = 28;
pub const HANDSHAKE_VERSION: u8 = 1;
pub const EPOCH_BYTES: usize = 16;
pub const REQUEST_BYTES: usize = 1;
pub const RESPONSE_BYTES: usize = 1 + 2 + EPOCH_BYTES;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct BackendInstance {
    epoch: [u8; EPOCH_BYTES],
}

static INSTANCE: OnceLock<BackendInstance> = OnceLock::new();

impl BackendInstance {
    fn generate() -> io::Result<Self> {
        let mut epoch = [0u8; EPOCH_BYTES];
        getrandom::getrandom(&mut epoch).map_err(|error| {
            io::Error::other(format!("backend instance entropy unavailable: {error}"))
        })?;
        if epoch.iter().all(|byte| *byte == 0) {
            epoch.zeroize();
            return Err(io::Error::other("backend instance epoch is invalid"));
        }
        Ok(Self { epoch })
    }

    fn encode(self) -> [u8; RESPONSE_BYTES] {
        let mut response = [0u8; RESPONSE_BYTES];
        response[0] = HANDSHAKE_VERSION;
        response[1..3].copy_from_slice(&PROTOCOL_VERSION.to_be_bytes());
        response[3..].copy_from_slice(&self.epoch);
        response
    }
}

pub fn initialize() -> io::Result<()> {
    if INSTANCE.get().is_some() {
        return Ok(());
    }
    let instance = BackendInstance::generate()?;
    INSTANCE
        .set(instance)
        .map_err(|_| io::Error::other("backend instance identity initialized twice"))
}

pub fn handle(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
    let valid = request.as_slice() == [HANDSHAKE_VERSION];
    request.zeroize();
    if !valid {
        return Err("backend handshake request rejected");
    }
    let instance = INSTANCE.get().ok_or("backend instance identity is unavailable")?;
    Ok(instance.encode().to_vec())
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

    #[test]
    fn handshake_request_is_exact_and_bounded() {
        initialize().unwrap();
        let response = handle(vec![HANDSHAKE_VERSION]).unwrap();
        assert_eq!(response.len(), RESPONSE_BYTES);
        assert!(handle(vec![]).is_err());
        assert!(handle(vec![HANDSHAKE_VERSION, 0]).is_err());
    }
}
