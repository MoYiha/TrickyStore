// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
use cleverestricky_attestation_core::CapturedPatchLevels;
use cleverestricky_certificate_core::{inspect_certificate, Error, SecurityLevel};

mod fixture {
    include!("rewrite.rs");

    pub(super) fn genuine_leaf_der() -> Vec<u8> {
        let document = parse_keybox_xml_bytes(VALID_EC).expect("fixture XML");
        let key = document.keys.first().expect("fixture key");
        let issuer_pem = key
            .certificates_pem
            .first()
            .expect("fixture issuer certificate");
        let normalized_issuer = normalized_pem(issuer_pem);
        let issuer =
            Certificate::from_pem(normalized_issuer.as_bytes()).expect("fixture issuer DER");
        synthetic_genuine_leaf(&issuer)
            .to_der()
            .expect("genuine DER")
    }

    pub(super) fn ordinary_certificate_der() -> Vec<u8> {
        let document = parse_keybox_xml_bytes(VALID_EC).expect("fixture XML");
        let key = document.keys.first().expect("fixture key");
        let issuer_pem = key
            .certificates_pem
            .first()
            .expect("fixture issuer certificate");
        let normalized_issuer = normalized_pem(issuer_pem);
        Certificate::from_pem(normalized_issuer.as_bytes())
            .expect("fixture certificate")
            .to_der()
            .expect("fixture DER")
    }
}

#[test]
fn inspection_returns_only_policy_inputs_needed_by_android_adapter() {
    let inspected = inspect_certificate(&fixture::genuine_leaf_der()).expect("inspection");
    assert_eq!(
        inspected.captured_patch_levels,
        CapturedPatchLevels {
            system: Some(202401),
            vendor: None,
            boot: None,
        }
    );
    assert_eq!(inspected.present_id_mask, 1 << 4);
    assert!(inspected.supports_module_hash);
    assert_eq!(inspected.original_boot_key, Some([0x21; 32]));
    assert_eq!(inspected.original_boot_hash, Some([0x31; 32]));
    assert_eq!(
        inspected.attestation_security_level,
        SecurityLevel::TrustedEnvironment
    );
    assert_eq!(
        inspected.keymint_security_level,
        SecurityLevel::TrustedEnvironment
    );
}

#[test]
fn certificate_without_attestation_extension_fails_closed() {
    assert_eq!(
        inspect_certificate(&fixture::ordinary_certificate_der()).unwrap_err(),
        Error::MissingAttestationExtension
    );
}
