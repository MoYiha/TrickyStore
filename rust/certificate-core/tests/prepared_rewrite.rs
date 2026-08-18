// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
mod fixture {
    include!("rewrite.rs");

    pub(super) fn run_prepared_fixture(xml: &[u8], algorithm: SigningAlgorithm) {
        let document = parse_keybox_xml_bytes(xml).expect("fixture XML");
        let key = document.keys.first().expect("fixture key");
        let private_key = normalize_private_key_pkcs8(&key.algorithm, &key.private_key_pem)
            .expect("fixture key DER");
        let issuer_pem = key
            .certificates_pem
            .first()
            .expect("fixture issuer certificate");
        let issuer = Certificate::from_pem(normalized_pem(issuer_pem).as_bytes())
            .expect("fixture issuer DER");
        let genuine = synthetic_genuine_leaf(&issuer);
        let genuine_der = genuine.to_der().expect("genuine DER");
        let issuer_der = issuer.to_der().expect("issuer DER");
        let ids = [AttestationIdOverride {
            tag: IMEI_TAG,
            value: b"new-imei",
        }];
        let prepared = cleverestricky_certificate_core::PreparedIssuer::new(
            &issuer_der,
            private_key.as_slice(),
            algorithm,
        )
        .expect("prepared issuer");

        let rewritten = cleverestricky_certificate_core::rewrite_certificate_prepared(
            &cleverestricky_certificate_core::PreparedCertificateRewriteRequest {
                genuine_leaf_der: &genuine_der,
                issuer: &prepared,
                patch_levels: PatchLevels {
                    system: PatchComponent::replace(202512),
                    vendor: PatchComponent::KEEP,
                    boot: PatchComponent::KEEP,
                },
                id_overrides: &ids,
                module_hash: Some(b"new-module-hash"),
                verified_boot_key: &BOOT_KEY,
                verified_boot_hash: &BOOT_HASH,
            },
        )
        .expect("prepared Rust certificate rewrite");

        assert_eq!(
            rewritten.captured_patch_levels,
            CapturedPatchLevels {
                system: Some(202401),
                vendor: None,
                boot: None,
            },
        );
        let output = Certificate::from_der(&rewritten.leaf_der).expect("rewritten certificate DER");
        assert_eq!(
            output.tbs_certificate().subject_public_key_info(),
            genuine.tbs_certificate().subject_public_key_info(),
        );
        assert_eq!(
            output.tbs_certificate().issuer(),
            issuer.tbs_certificate().subject(),
        );
        verify_signature(&output, &issuer, algorithm);
    }

    pub(super) fn ec() -> &'static [u8] {
        VALID_EC
    }

    pub(super) fn rsa() -> &'static [u8] {
        VALID_RSA
    }
}

#[test]
fn prepared_ec_issuer_rewrites_and_signs_without_per_call_key_parse() {
    fixture::run_prepared_fixture(
        fixture::ec(),
        cleverestricky_certificate_core::SigningAlgorithm::EcP256Sha256,
    );
}

#[test]
fn prepared_rsa_issuer_rewrites_and_signs_without_per_call_key_parse() {
    fixture::run_prepared_fixture(
        fixture::rsa(),
        cleverestricky_certificate_core::SigningAlgorithm::RsaPkcs1Sha256,
    );
}
