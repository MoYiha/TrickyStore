// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_crl_core::{CrlIndex, MAX_CRL_BYTES, MAX_SERIAL_BYTES, MAX_SPKI_BYTES};
use zeroize::Zeroize;

const WIRE_VERSION: u8 = 1;
const REQUEST_PREFIX_BYTES: usize = 6;
const RESPONSE_PREFIX_BYTES: usize = 11;
pub const MAX_QUERY_COUNT: usize = 16 * 1024;
pub const MAX_REQUEST_BYTES: usize = 24 * 1024 * 1024;
pub const MAX_RESPONSE_BYTES: usize = RESPONSE_PREFIX_BYTES + MAX_QUERY_COUNT.div_ceil(8);

pub fn check_batch(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
    let result = check_batch_inner(&request);
    request.zeroize();
    result
}

fn check_batch_inner(request: &[u8]) -> Result<Vec<u8>, &'static str> {
    if request.len() < REQUEST_PREFIX_BYTES || request.len() > MAX_REQUEST_BYTES {
        return Err("CRL batch request exceeds configured bound");
    }

    let crl_len = read_u32(request, 0)?;
    let query_count = read_u16(request, 4)?;
    if crl_len == 0 || crl_len > MAX_CRL_BYTES || query_count > MAX_QUERY_COUNT {
        return Err("CRL batch request fields exceed configured bound");
    }
    let crl_end = REQUEST_PREFIX_BYTES
        .checked_add(crl_len)
        .ok_or("CRL batch request length overflow")?;
    let crl = request
        .get(REQUEST_PREFIX_BYTES..crl_end)
        .ok_or("CRL batch request is truncated")?;
    let index = CrlIndex::parse(crl).map_err(|_| "CRL rejected")?;

    let response_len = RESPONSE_PREFIX_BYTES
        .checked_add(query_count.div_ceil(8))
        .ok_or("CRL batch response length overflow")?;
    if response_len > MAX_RESPONSE_BYTES {
        return Err("CRL batch response exceeds configured bound");
    }
    let mut response = Vec::with_capacity(response_len);
    response.push(WIRE_VERSION);
    response.extend_from_slice(
        &u32::try_from(index.raw_entry_count())
            .map_err(|_| "CRL raw count exceeds wire bound")?
            .to_be_bytes(),
    );
    response.extend_from_slice(
        &u32::try_from(index.normalized_count())
            .map_err(|_| "CRL normalized count exceeds wire bound")?
            .to_be_bytes(),
    );
    response.extend_from_slice(
        &u16::try_from(query_count)
            .map_err(|_| "CRL query count exceeds wire bound")?
            .to_be_bytes(),
    );
    response.resize(response_len, 0);

    let mut cursor = Cursor::new(request, crl_end);
    for query_index in 0..query_count {
        let serial_len = cursor.read_u16()?;
        let spki_len = cursor.read_u32()?;
        if serial_len == 0
            || serial_len > MAX_SERIAL_BYTES
            || spki_len == 0
            || spki_len > MAX_SPKI_BYTES
        {
            return Err("CRL query fields exceed configured bound");
        }
        let serial = cursor.read_bytes(serial_len)?;
        let spki = cursor.read_bytes(spki_len)?;
        if index
            .is_revoked(serial, spki)
            .map_err(|_| "CRL query rejected")?
        {
            response[RESPONSE_PREFIX_BYTES + query_index / 8] |= 1u8 << (query_index % 8);
        }
    }
    if !cursor.is_at_end() {
        return Err("CRL batch request has trailing bytes");
    }
    Ok(response)
}

fn read_u16(input: &[u8], offset: usize) -> Result<usize, &'static str> {
    let end = offset
        .checked_add(2)
        .ok_or("CRL batch wire length overflow")?;
    let bytes: [u8; 2] = input
        .get(offset..end)
        .ok_or("CRL batch wire is truncated")?
        .try_into()
        .map_err(|_| "CRL batch wire is truncated")?;
    Ok(u16::from_be_bytes(bytes) as usize)
}

fn read_u32(input: &[u8], offset: usize) -> Result<usize, &'static str> {
    let end = offset
        .checked_add(4)
        .ok_or("CRL batch wire length overflow")?;
    let bytes: [u8; 4] = input
        .get(offset..end)
        .ok_or("CRL batch wire is truncated")?
        .try_into()
        .map_err(|_| "CRL batch wire is truncated")?;
    Ok(u32::from_be_bytes(bytes) as usize)
}

struct Cursor<'a> {
    bytes: &'a [u8],
    offset: usize,
}

impl<'a> Cursor<'a> {
    fn new(bytes: &'a [u8], offset: usize) -> Self {
        Self { bytes, offset }
    }

    fn read_u16(&mut self) -> Result<usize, &'static str> {
        let value = read_u16(self.bytes, self.offset)?;
        self.offset = self
            .offset
            .checked_add(2)
            .ok_or("CRL batch wire length overflow")?;
        Ok(value)
    }

    fn read_u32(&mut self) -> Result<usize, &'static str> {
        let value = read_u32(self.bytes, self.offset)?;
        self.offset = self
            .offset
            .checked_add(4)
            .ok_or("CRL batch wire length overflow")?;
        Ok(value)
    }

    fn read_bytes(&mut self, length: usize) -> Result<&'a [u8], &'static str> {
        let end = self
            .offset
            .checked_add(length)
            .ok_or("CRL batch wire length overflow")?;
        let value = self
            .bytes
            .get(self.offset..end)
            .ok_or("CRL batch wire is truncated")?;
        self.offset = end;
        Ok(value)
    }

    fn is_at_end(&self) -> bool {
        self.offset == self.bytes.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn encode_request(crl: &[u8], queries: &[(&[u8], &[u8])]) -> Vec<u8> {
        let mut output = Vec::new();
        output.extend_from_slice(&(crl.len() as u32).to_be_bytes());
        output.extend_from_slice(&(queries.len() as u16).to_be_bytes());
        output.extend_from_slice(crl);
        for (serial, spki) in queries {
            output.extend_from_slice(&(serial.len() as u16).to_be_bytes());
            output.extend_from_slice(&(spki.len() as u32).to_be_bytes());
            output.extend_from_slice(serial);
            output.extend_from_slice(spki);
        }
        output
    }

    fn response_bit(response: &[u8], index: usize) -> bool {
        response[RESPONSE_PREFIX_BYTES + index / 8] & (1u8 << (index % 8)) != 0
    }

    #[test]
    fn batch_matches_serial_and_digest_queries_without_exporting_normalized_set() {
        let crl = br#"{"entries":{"1":0,"900150983cd24fb0d6963f7d28e17f72":0}}"#;
        let request = encode_request(
            crl,
            &[
                (&[0x01], b"unrelated"),
                (&[0x02], b"abc"),
                (&[0x03], b"def"),
            ],
        );
        let response = check_batch(request).unwrap();

        assert_eq!(response[0], WIRE_VERSION);
        assert_eq!(u32::from_be_bytes(response[1..5].try_into().unwrap()), 2);
        assert!(u32::from_be_bytes(response[5..9].try_into().unwrap()) >= 2);
        assert_eq!(u16::from_be_bytes(response[9..11].try_into().unwrap()), 3);
        assert!(response_bit(&response, 0));
        assert!(response_bit(&response, 1));
        assert!(!response_bit(&response, 2));
    }

    #[test]
    fn zero_query_request_validates_and_counts_crl() {
        let response = check_batch(encode_request(br#"{"entries":{"255":0}}"#, &[])).unwrap();
        assert_eq!(response.len(), RESPONSE_PREFIX_BYTES);
        assert_eq!(u32::from_be_bytes(response[1..5].try_into().unwrap()), 1);
        assert_eq!(u16::from_be_bytes(response[9..11].try_into().unwrap()), 0);
    }

    #[test]
    fn malformed_lengths_trailing_bytes_and_oversized_fields_fail_closed() {
        assert!(check_batch(vec![0; REQUEST_PREFIX_BYTES]).is_err());

        let mut trailing = encode_request(br#"{"entries":{}}"#, &[]);
        trailing.push(1);
        assert!(check_batch(trailing).is_err());

        let oversized_serial = vec![0u8; MAX_SERIAL_BYTES + 1];
        let request = encode_request(br#"{"entries":{}}"#, &[(&oversized_serial, b"spki")]);
        assert!(check_batch(request).is_err());
    }

    #[test]
    fn query_and_total_request_bounds_are_explicit() {
        assert_eq!(
            MAX_RESPONSE_BYTES,
            RESPONSE_PREFIX_BYTES + MAX_QUERY_COUNT / 8
        );
        let crl = br#"{"entries":{}}"#;
        let mut request = Vec::new();
        request.extend_from_slice(&(crl.len() as u32).to_be_bytes());
        request.extend_from_slice(&u16::MAX.to_be_bytes());
        request.extend_from_slice(crl);
        assert!(check_batch(request).is_err());

        let oversized = vec![0u8; MAX_REQUEST_BYTES + 1];
        assert!(check_batch(oversized).is_err());
    }
}
