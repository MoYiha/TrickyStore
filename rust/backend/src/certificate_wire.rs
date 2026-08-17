// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_certificate_core::{
    inspect_certificate, rewrite_certificate, AttestationIdOverride, CertificateRewriteRequest,
    PatchComponent, PatchLevels, SigningAlgorithm, MAX_ATTESTATION_ID_BYTES,
    MAX_CERTIFICATE_DER_BYTES, MAX_MODULE_HASH_BYTES, MAX_PRIVATE_KEY_DER_BYTES,
};
use zeroize::Zeroize;

const WIRE_VERSION: u8 = 1;
const SIGNING_EC_P256_SHA256: u8 = 1;
const SIGNING_RSA_PKCS1_SHA256: u8 = 2;
const PATCH_KEEP: u8 = 0;
const PATCH_OMIT: u8 = 1;
const PATCH_REPLACE: u8 = 2;
const MAX_ID_OVERRIDES: usize = 9;
const REWRITE_FIXED_BYTES: usize = 1 + 1 + 3 * 5 + 1 + 2 + 3 * 4 + 2 * 32;
const MAX_ID_WIRE_BYTES: usize = MAX_ID_OVERRIDES * (2 + 2 + MAX_ATTESTATION_ID_BYTES);

pub const MAX_INSPECT_REQUEST_BYTES: usize = MAX_CERTIFICATE_DER_BYTES;
pub const INSPECT_RESPONSE_BYTES: usize = 1 + 1 + 2 + 3 * 5 + 2 * 32;
pub const MAX_REWRITE_REQUEST_BYTES: usize = REWRITE_FIXED_BYTES
    + MAX_ID_WIRE_BYTES
    + MAX_MODULE_HASH_BYTES
    + MAX_CERTIFICATE_DER_BYTES * 2
    + MAX_PRIVATE_KEY_DER_BYTES;
pub const MAX_REWRITE_RESPONSE_BYTES: usize = MAX_CERTIFICATE_DER_BYTES;

pub fn inspect_and_encode(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
    let result = (|| {
        if request.is_empty() || request.len() > MAX_INSPECT_REQUEST_BYTES {
            return Err("certificate inspection request rejected");
        }
        let inspection =
            inspect_certificate(&request).map_err(|_| "certificate inspection rejected")?;
        let mut output = Vec::with_capacity(INSPECT_RESPONSE_BYTES);
        output.push(WIRE_VERSION);
        let mut flags = u8::from(inspection.supports_module_hash);
        if inspection.original_boot_key.is_some() {
            flags |= 1 << 1;
        }
        if inspection.original_boot_hash.is_some() {
            flags |= 1 << 2;
        }
        output.push(flags);
        output.extend_from_slice(&inspection.present_id_mask.to_be_bytes());
        encode_optional_i32(&mut output, inspection.captured_patch_levels.system);
        encode_optional_i32(&mut output, inspection.captured_patch_levels.vendor);
        encode_optional_i32(&mut output, inspection.captured_patch_levels.boot);
        output.extend_from_slice(&inspection.original_boot_key.unwrap_or([0; 32]));
        output.extend_from_slice(&inspection.original_boot_hash.unwrap_or([0; 32]));
        debug_assert_eq!(output.len(), INSPECT_RESPONSE_BYTES);
        Ok(output)
    })();
    request.zeroize();
    result
}

pub fn rewrite_and_encode(mut request: Vec<u8>) -> Result<Vec<u8>, &'static str> {
    let result = (|| {
        if request.len() < REWRITE_FIXED_BYTES || request.len() > MAX_REWRITE_REQUEST_BYTES {
            return Err("certificate rewrite request rejected");
        }
        let parsed = parse_rewrite_request(&request)?;
        rewrite_certificate(&CertificateRewriteRequest {
            genuine_leaf_der: parsed.genuine_leaf_der,
            issuer_certificate_der: parsed.issuer_certificate_der,
            issuer_private_key_pkcs8: parsed.issuer_private_key_pkcs8,
            signing_algorithm: parsed.signing_algorithm,
            patch_levels: parsed.patch_levels,
            id_overrides: &parsed.id_overrides,
            module_hash: parsed.module_hash,
            verified_boot_key: parsed.verified_boot_key,
            verified_boot_hash: parsed.verified_boot_hash,
        })
        .map(|rewritten| rewritten.leaf_der)
        .map_err(|_| "certificate rewrite rejected")
    })();
    request.zeroize();
    result
}

struct ParsedRewrite<'a> {
    signing_algorithm: SigningAlgorithm,
    patch_levels: PatchLevels,
    id_overrides: Vec<AttestationIdOverride<'a>>,
    module_hash: Option<&'a [u8]>,
    genuine_leaf_der: &'a [u8],
    issuer_certificate_der: &'a [u8],
    issuer_private_key_pkcs8: &'a [u8],
    verified_boot_key: &'a [u8; 32],
    verified_boot_hash: &'a [u8; 32],
}

fn parse_rewrite_request(request: &[u8]) -> Result<ParsedRewrite<'_>, &'static str> {
    let mut cursor = Cursor::new(request);
    if cursor.read_u8()? != WIRE_VERSION {
        return Err("unsupported certificate wire version");
    }
    let signing_algorithm = match cursor.read_u8()? {
        SIGNING_EC_P256_SHA256 => SigningAlgorithm::EcP256Sha256,
        SIGNING_RSA_PKCS1_SHA256 => SigningAlgorithm::RsaPkcs1Sha256,
        _ => return Err("unsupported certificate signing algorithm"),
    };
    let patch_levels = PatchLevels {
        system: read_patch(&mut cursor)?,
        vendor: read_patch(&mut cursor)?,
        boot: read_patch(&mut cursor)?,
    };
    let id_count = cursor.read_u8()? as usize;
    if id_count > MAX_ID_OVERRIDES {
        return Err("too many attestation ID overrides");
    }
    let module_hash_len = cursor.read_u16()? as usize;
    if module_hash_len > MAX_MODULE_HASH_BYTES {
        return Err("module hash exceeds certificate wire bound");
    }
    let genuine_leaf_len = cursor.read_u32_as_usize()?;
    let issuer_certificate_len = cursor.read_u32_as_usize()?;
    let issuer_private_key_len = cursor.read_u32_as_usize()?;
    if genuine_leaf_len == 0
        || genuine_leaf_len > MAX_CERTIFICATE_DER_BYTES
        || issuer_certificate_len == 0
        || issuer_certificate_len > MAX_CERTIFICATE_DER_BYTES
        || issuer_private_key_len == 0
        || issuer_private_key_len > MAX_PRIVATE_KEY_DER_BYTES
    {
        return Err("certificate DER field exceeds wire bound");
    }
    let verified_boot_key: &[u8; 32] = cursor
        .read_exact(32)?
        .try_into()
        .map_err(|_| "invalid verified boot key")?;
    let verified_boot_hash: &[u8; 32] = cursor
        .read_exact(32)?
        .try_into()
        .map_err(|_| "invalid verified boot hash")?;
    if verified_boot_key.iter().all(|byte| *byte == 0)
        || verified_boot_hash.iter().all(|byte| *byte == 0)
    {
        return Err("verified boot digest is unavailable");
    }

    let mut id_overrides = Vec::new();
    id_overrides
        .try_reserve_exact(id_count)
        .map_err(|_| "attestation ID allocation failed")?;
    let mut seen_tags = [0u16; MAX_ID_OVERRIDES];
    for seen_count in 0..id_count {
        let tag = cursor.read_u16()?;
        let length = cursor.read_u16()? as usize;
        if length == 0 || length > MAX_ATTESTATION_ID_BYTES {
            return Err("attestation ID override exceeds wire bound");
        }
        if seen_tags[..seen_count].contains(&tag) {
            return Err("duplicate attestation ID override");
        }
        seen_tags[seen_count] = tag;
        let value = cursor.read_exact(length)?;
        id_overrides.push(AttestationIdOverride {
            tag: u32::from(tag),
            value,
        });
    }
    let module_hash = if module_hash_len == 0 {
        None
    } else {
        Some(cursor.read_exact(module_hash_len)?)
    };
    let genuine_leaf_der = cursor.read_exact(genuine_leaf_len)?;
    let issuer_certificate_der = cursor.read_exact(issuer_certificate_len)?;
    let issuer_private_key_pkcs8 = cursor.read_exact(issuer_private_key_len)?;
    if !cursor.is_at_end() {
        return Err("trailing certificate wire bytes");
    }

    Ok(ParsedRewrite {
        signing_algorithm,
        patch_levels,
        id_overrides,
        module_hash,
        genuine_leaf_der,
        issuer_certificate_der,
        issuer_private_key_pkcs8,
        verified_boot_key,
        verified_boot_hash,
    })
}

fn read_patch(cursor: &mut Cursor<'_>) -> Result<PatchComponent, &'static str> {
    let disposition = cursor.read_u8()?;
    let value = cursor.read_i32()?;
    match disposition {
        PATCH_KEEP if value == 0 => Ok(PatchComponent::KEEP),
        PATCH_OMIT if value == 0 => Ok(PatchComponent::OMIT),
        PATCH_REPLACE if value > 0 => Ok(PatchComponent::replace(value)),
        _ => Err("invalid certificate patch component"),
    }
}

fn encode_optional_i32(output: &mut Vec<u8>, value: Option<i32>) {
    match value {
        Some(value) => {
            output.push(1);
            output.extend_from_slice(&value.to_be_bytes());
        }
        None => {
            output.push(0);
            output.extend_from_slice(&0i32.to_be_bytes());
        }
    }
}

struct Cursor<'a> {
    bytes: &'a [u8],
    offset: usize,
}

impl<'a> Cursor<'a> {
    fn new(bytes: &'a [u8]) -> Self {
        Self { bytes, offset: 0 }
    }

    fn read_u8(&mut self) -> Result<u8, &'static str> {
        Ok(*self
            .read_exact(1)?
            .first()
            .ok_or("truncated certificate wire")?)
    }

    fn read_u16(&mut self) -> Result<u16, &'static str> {
        let bytes: [u8; 2] = self
            .read_exact(2)?
            .try_into()
            .map_err(|_| "truncated certificate wire")?;
        Ok(u16::from_be_bytes(bytes))
    }

    fn read_i32(&mut self) -> Result<i32, &'static str> {
        let bytes: [u8; 4] = self
            .read_exact(4)?
            .try_into()
            .map_err(|_| "truncated certificate wire")?;
        Ok(i32::from_be_bytes(bytes))
    }

    fn read_u32_as_usize(&mut self) -> Result<usize, &'static str> {
        let bytes: [u8; 4] = self
            .read_exact(4)?
            .try_into()
            .map_err(|_| "truncated certificate wire")?;
        usize::try_from(u32::from_be_bytes(bytes)).map_err(|_| "certificate wire length overflow")
    }

    fn read_exact(&mut self, length: usize) -> Result<&'a [u8], &'static str> {
        let end = self
            .offset
            .checked_add(length)
            .ok_or("certificate wire length overflow")?;
        let value = self
            .bytes
            .get(self.offset..end)
            .ok_or("truncated certificate wire")?;
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

    fn patch(disposition: u8, value: i32, out: &mut Vec<u8>) {
        out.push(disposition);
        out.extend_from_slice(&value.to_be_bytes());
    }

    fn minimal_request() -> Vec<u8> {
        let mut output = Vec::new();
        output.push(WIRE_VERSION);
        output.push(SIGNING_EC_P256_SHA256);
        patch(PATCH_KEEP, 0, &mut output);
        patch(PATCH_OMIT, 0, &mut output);
        patch(PATCH_REPLACE, 20251205, &mut output);
        output.push(1);
        output.extend_from_slice(&3u16.to_be_bytes());
        output.extend_from_slice(&1u32.to_be_bytes());
        output.extend_from_slice(&1u32.to_be_bytes());
        output.extend_from_slice(&1u32.to_be_bytes());
        output.extend_from_slice(&[0x11; 32]);
        output.extend_from_slice(&[0x22; 32]);
        output.extend_from_slice(&714u16.to_be_bytes());
        output.extend_from_slice(&4u16.to_be_bytes());
        output.extend_from_slice(b"imei");
        output.extend_from_slice(b"mod");
        output.push(1);
        output.push(2);
        output.push(3);
        output
    }

    #[test]
    fn strict_rewrite_wire_parses_bounded_fields() {
        let input = minimal_request();
        let parsed = parse_rewrite_request(&input).unwrap();
        assert_eq!(parsed.signing_algorithm, SigningAlgorithm::EcP256Sha256);
        assert_eq!(parsed.patch_levels.system, PatchComponent::KEEP);
        assert_eq!(parsed.patch_levels.vendor, PatchComponent::OMIT);
        assert_eq!(parsed.patch_levels.boot, PatchComponent::replace(20251205));
        assert_eq!(parsed.id_overrides.len(), 1);
        assert_eq!(parsed.id_overrides[0].tag, 714);
        assert_eq!(parsed.id_overrides[0].value, b"imei");
        assert_eq!(parsed.module_hash, Some(b"mod".as_slice()));
        assert_eq!(parsed.genuine_leaf_der, &[1]);
        assert_eq!(parsed.issuer_certificate_der, &[2]);
        assert_eq!(parsed.issuer_private_key_pkcs8, &[3]);
    }

    #[test]
    fn strict_rewrite_wire_rejects_trailing_duplicate_and_noncanonical_patch_fields() {
        let mut trailing = minimal_request();
        trailing.push(0);
        assert!(parse_rewrite_request(&trailing).is_err());

        let mut noncanonical = minimal_request();
        noncanonical[2] = PATCH_KEEP;
        noncanonical[3..7].copy_from_slice(&1i32.to_be_bytes());
        assert!(parse_rewrite_request(&noncanonical).is_err());

        let mut duplicate = minimal_request();
        duplicate[17] = 2;
        let second = [0x02, 0xca, 0x00, 0x01, b'x'];
        let insert_at = REWRITE_FIXED_BYTES + 8;
        duplicate.splice(insert_at..insert_at, second);
        assert!(parse_rewrite_request(&duplicate).is_err());
    }

    #[test]
    fn inspection_rejects_empty_and_oversized_inputs() {
        assert!(inspect_and_encode(Vec::new()).is_err());
        let oversized = vec![0x30; MAX_INSPECT_REQUEST_BYTES + 1];
        assert!(inspect_and_encode(oversized).is_err());
    }
}
