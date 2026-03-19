// LOCAL PKI & DER ENGINE
// Native Rust PKI engine for isolated environments.

use ring::{rand, signature};
use ring::signature::KeyPair;
use std::fs::File;
use std::io::Read;
use ring::hmac;

pub struct CertEngine {
    seed: [u8; 32],
}

impl CertEngine {
    pub fn new() -> Self {
        let mut seed = [0u8; 32];
        if let Ok(mut file) = File::open("/dev/random") {
            let _ = file.read_exact(&mut seed);
        }
        Self { seed }
    }

    pub fn generate_ec_p256_keypair() -> Result<Vec<u8>, &'static str> {
        let rng = rand::SystemRandom::new();
        let pkcs8_bytes = signature::EcdsaKeyPair::generate_pkcs8(
            &signature::ECDSA_P256_SHA256_ASN1_SIGNING,
            &rng,
        ).map_err(|_| "Failed to generate keypair")?;

        Ok(pkcs8_bytes.as_ref().to_vec())
    }

    pub fn calculate_include_unique_id(&self, input: &[u8]) -> Vec<u8> {
        // Hardware ID Binding: Calculate using HMAC-SHA256 with persistent 32-byte seed
        let key = hmac::Key::new(hmac::HMAC_SHA256, &self.seed);
        let tag = hmac::sign(&key, input);
        tag.as_ref().to_vec()
    }

    pub fn validate_challenge(&self, challenge: &[u8]) -> Result<(), i32> {
        // Buffer Safety: Reject attestation_challenge inputs exceeding 128 bytes with INVALID_INPUT_LENGTH (-21)
        if challenge.len() > 128 {
            return Err(-21); // INVALID_INPUT_LENGTH
        }
        Ok(())
    }
}
