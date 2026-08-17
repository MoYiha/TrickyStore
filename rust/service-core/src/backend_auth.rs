// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use zeroize::Zeroize;

pub const BACKEND_AUTH_ENV: &str = "CLEVERES_TRICKY_BACKEND_AUTH";
pub const BACKEND_AUTH_BYTES: usize = 32;
pub const BACKEND_AUTH_HEX_BYTES: usize = BACKEND_AUTH_BYTES * 2;

pub fn encode_hex(token: &[u8; BACKEND_AUTH_BYTES]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut encoded = [0u8; BACKEND_AUTH_HEX_BYTES];
    for (index, byte) in token.iter().copied().enumerate() {
        encoded[index * 2] = HEX[(byte >> 4) as usize];
        encoded[index * 2 + 1] = HEX[(byte & 0x0f) as usize];
    }
    // SAFETY is unnecessary: every byte was selected from the ASCII table above.
    String::from_utf8(encoded.to_vec()).expect("hex alphabet is valid UTF-8")
}

pub fn decode_hex(value: &str) -> Option<[u8; BACKEND_AUTH_BYTES]> {
    if value.len() != BACKEND_AUTH_HEX_BYTES || !value.is_ascii() {
        return None;
    }
    let input = value.as_bytes();
    let mut token = [0u8; BACKEND_AUTH_BYTES];
    for index in 0..BACKEND_AUTH_BYTES {
        let high = decode_nibble(input[index * 2])?;
        let low = decode_nibble(input[index * 2 + 1])?;
        token[index] = (high << 4) | low;
    }
    Some(token)
}

pub fn matches(expected: &[u8; BACKEND_AUTH_BYTES], candidate: &[u8]) -> bool {
    if candidate.len() != BACKEND_AUTH_BYTES {
        return false;
    }
    let mut difference = 0u8;
    for (left, right) in expected.iter().zip(candidate) {
        difference |= left ^ right;
    }
    difference == 0
}

pub fn decode_and_zeroize(value: &mut String) -> Option<[u8; BACKEND_AUTH_BYTES]> {
    let decoded = decode_hex(value);
    value.zeroize();
    decoded
}

fn decode_nibble(byte: u8) -> Option<u8> {
    match byte {
        b'0'..=b'9' => Some(byte - b'0'),
        b'a'..=b'f' => Some(byte - b'a' + 10),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn auth_token_round_trips() {
        let mut token = [0u8; BACKEND_AUTH_BYTES];
        for (index, byte) in token.iter_mut().enumerate() {
            *byte = index as u8;
        }
        let encoded = encode_hex(&token);
        assert_eq!(encoded.len(), BACKEND_AUTH_HEX_BYTES);
        assert_eq!(decode_hex(&encoded), Some(token));
    }

    #[test]
    fn decoder_rejects_noncanonical_or_malformed_input() {
        assert!(decode_hex("").is_none());
        assert!(decode_hex(&"00".repeat(BACKEND_AUTH_BYTES - 1)).is_none());
        assert!(decode_hex(&"00".repeat(BACKEND_AUTH_BYTES + 1)).is_none());
        assert!(decode_hex(&"GG".repeat(BACKEND_AUTH_BYTES)).is_none());
        assert!(decode_hex(&"AA".repeat(BACKEND_AUTH_BYTES)).is_none());
    }

    #[test]
    fn comparison_requires_exact_token() {
        let expected = [0x5au8; BACKEND_AUTH_BYTES];
        let same = [0x5au8; BACKEND_AUTH_BYTES];
        let mut different = same;
        different[BACKEND_AUTH_BYTES - 1] ^= 1;
        assert!(matches(&expected, &same));
        assert!(!matches(&expected, &different));
        assert!(!matches(&expected, &same[..BACKEND_AUTH_BYTES - 1]));
    }

    #[test]
    fn decode_helper_clears_environment_copy() {
        let token = [0xabu8; BACKEND_AUTH_BYTES];
        let mut encoded = encode_hex(&token);
        assert_eq!(decode_and_zeroize(&mut encoded), Some(token));
        assert!(encoded.bytes().all(|byte| byte == 0));
    }
}
