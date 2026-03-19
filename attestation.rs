// Strict AOSP Schema: attestation_record.h
// Implements DER specification and sorting.

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
        let mut encoded = Vec::new();
        encoded.push(0x31); // SET tag
        let mut inner = Vec::new();
        for &val in values {
            inner.push(0x02); // INTEGER tag
            inner.push(0x01); // Length 1
            inner.push(val as u8); // Value
        }
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
