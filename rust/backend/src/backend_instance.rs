// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_service_core::backend_auth::{
    decode_and_zeroize, matches, BACKEND_AUTH_BYTES, BACKEND_AUTH_ENV,
};
use cleverestricky_service_core::ipc::PROTOCOL_VERSION;
use std::env;
use std::io;
use std::sync::OnceLock;
use zeroize::Zeroize;

pub const OP_BACKEND_PING: u16 = 28;
pub const HANDSHAKE_VERSION: u8 = 1;
pub const EPOCH_BYTES: usize = 16;
pub const REQUEST_BYTES: usize = 1 + BACKEND_AUTH_BYTES;
pub const RESPONSE_BYTES: usize = 1 + 2 + EPOCH_BYTES;

#[derive(Debug, Eq, PartialEq)]
pub struct BackendInstance {
    epoch: [u8; EPOCH_BYTES],
    auth: [u8; BACKEND_AUTH_BYTES],
}

static INSTANCE: OnceLock<BackendInstance> = OnceLock::new();

impl BackendInstance {
    fn generate(auth: [u8; BACKEND_AUTH_BYTES]) -> io::Result<Self> {
        let mut epoch = [0u8; EPOCH_BYTES];
        getrandom::getrandom(&mut epoch).map_err(|error| {
            io::Error::other(format!("backend instance entropy unavailable: {error}"))
        })?;
        if epoch.iter().all(|byte| *byte == 0) {
            epoch.zeroize();
            return Err(io::Error::other("backend instance epoch is invalid"));
        }
        Ok(Self { epoch, auth })
    }

    fn encode(&self) -> [u8; RESPONSE_BYTES] {
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
    let mut encoded = env::var(BACKEND_AUTH_ENV)
        .map_err(|_| io::Error::other("backend capability is unavailable"))?;
    let auth = decode_and_zeroize(&mut encoded)
        .ok_or_else(|| io::Error::other("backend capability is invalid"))?;
    if auth.iter().all(|byte| *byte == 0) {
        return Err(io::Error::other("backend capability is invalid"));
    }
    let instance = BackendInstance::generate(auth)?;
    INSTANCE
        .set(instance)
        .map_err(|_| io::Error::other("backend instance identity initialized twice"))
}

#[cfg(test)]
pub fn initialize_for_test(auth: [u8; BACKEND_AUTH_BYTES]) -> io::Result<()> {
    if auth.iter().all(|byte| *byte == 0) {
        return Err(io::Error::other("backend capability is invalid"));
    }
    if let Some(existing) = INSTANCE.get() {
        return if matches(&existing.auth, &auth) {
            Ok(())
        } else {
            Err(io::Error::other("backend instance already initialized"))
        };
    }
    INSTANCE
        .set(BackendInstance::generate(auth)?)
        .map_err(|_| io::Error::other("backend instance already initialized"))
}

pub fn handle(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
    let instance = INSTANCE
        .get()
        .ok_or("backend instance identity is unavailable")?;
    let valid = request.len() == REQUEST_BYTES
        && request[0] == HANDSHAKE_VERSION
        && matches(&instance.auth, &request[1..]);
    request.zeroize();
    if !valid {
        return Err("backend handshake request rejected");
    }
    Ok(instance.encode().to_vec())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generated_epoch_is_nonzero_and_wire_is_fixed() {
        let first = BackendInstance::generate([0x11; BACKEND_AUTH_BYTES]).unwrap();
        let second = BackendInstance::generate([0x11; BACKEND_AUTH_BYTES]).unwrap();
        assert_ne!(first.epoch, second.epoch);
        let response = first.encode();
        assert_eq!(response.len(), RESPONSE_BYTES);
        assert_eq!(response[0], HANDSHAKE_VERSION);
        assert_eq!(
            u16::from_be_bytes([response[1], response[2]]),
            PROTOCOL_VERSION
        );
        assert!(response[3..].iter().any(|byte| *byte != 0));
    }

    #[test]
    fn handshake_request_requires_exact_capability() {
        let instance = BackendInstance::generate([0x5a; BACKEND_AUTH_BYTES]).unwrap();
        let mut valid = vec![0u8; REQUEST_BYTES];
        valid[0] = HANDSHAKE_VERSION;
        valid[1..].fill(0x5a);
        assert!(matches(&instance.auth, &valid[1..]));

        let mut wrong = valid.clone();
        wrong[REQUEST_BYTES - 1] ^= 1;
        assert!(!matches(&instance.auth, &wrong[1..]));
        assert!(!matches(&instance.auth, &valid[1..BACKEND_AUTH_BYTES]));
    }
}
