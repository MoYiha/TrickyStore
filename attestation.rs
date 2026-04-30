// Strict AOSP Schema: attestation_record.h
// Implements DER specification and sorting.
//
// FFI Safety: AttestationRecord is a pure-Rust type; if it is ever exposed
// across the FFI boundary via #[no_mangle] extern "C" functions, each such
// function MUST be wrapped with std::panic::catch_unwind to prevent panics
// from unwinding into C++ (undefined behaviour).  See libcertgen.rs for the
// canonical pattern.

#[derive(Debug, Clone)]
pub struct AttestationRecord {
    pub challenge: Vec<u8>,
    pub subject_cn: String,
}

impl AttestationRecord {
    pub fn new(challenge: Vec<u8>) -> Result<Self, i32> {
        if challenge.len() > 128 {
            return Err(-21); // INVALID_INPUT_LENGTH (-21)
        }

        Ok(Self {
            challenge,
            // Subject Normalization: Hardcode Subject CN precisely
            subject_cn: "Android Keystore Key".to_string(),
        })
    }

    pub fn encode_padding_block_mode(&self, values: &[i32]) -> Vec<u8> {
        // Encode PADDING and BLOCK_MODE tags as ASN.1 SET OF INTEGER
        // Simplified DER encoding for SET OF INTEGER
        let mut inner = Vec::new();
        for &val in values {
            inner.push(0x02); // INTEGER tag
            inner.push(0x01); // Length 1
            inner.push(val as u8); // Value
        }
        // DER SET length must fit in a single byte for this simplified encoder.
        // If it does not, return an empty buffer to signal the caller that the
        // input is too large for this encoding rather than producing malformed DER.
        if inner.len() > u8::MAX as usize {
            return Vec::new();
        }
        let mut encoded = Vec::with_capacity(2 + inner.len());
        encoded.push(0x31); // SET tag
        encoded.push(inner.len() as u8); // Length of SET
        encoded.extend(inner);
        encoded
    }

    pub fn sort_authorization_list(&self, mut tags: Vec<u32>) -> Vec<u32> {
        // Strict Sorting: Sort all AuthorizationList tags in strict ascending order
        tags.sort_unstable();
        tags
    }
}
