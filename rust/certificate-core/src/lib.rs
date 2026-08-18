// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#![forbid(unsafe_code)]

use cleverestricky_attestation_core::{
    rewrite_extension, AttestationIdOverride, CapturedPatchLevels, PatchLevels, RewriteRequest,
};
use der::asn1::{BitString, OctetString};
use der::{Decode, Encode};
use p256::ecdsa::{
    Signature as EcSignature, SigningKey as EcSigningKey, VerifyingKey as EcVerifyingKey,
};
use p256::pkcs8::{DecodePrivateKey as _, DecodePublicKey as _};
use rsa::pkcs1v15::{
    Signature as RsaSignature, SigningKey as RsaSigningKey, VerifyingKey as RsaVerifyingKey,
};
use sha2::Sha256;
use signature::{SignatureEncoding, Signer, Verifier};
use std::fmt;
use x509_cert::spki::{DynSignatureAlgorithmIdentifier, ObjectIdentifier};
use x509_cert::{Certificate, TbsCertificate};

pub const MAX_CERTIFICATE_DER_BYTES: usize = 256 * 1024;
pub const MAX_PRIVATE_KEY_DER_BYTES: usize = 3 * MAX_CERTIFICATE_DER_BYTES;
pub const ANDROID_ATTESTATION_OID: ObjectIdentifier =
    ObjectIdentifier::new_unwrap("1.3.6.1.4.1.11129.2.1.17");

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum SigningAlgorithm {
    EcP256Sha256,
    RsaPkcs1Sha256,
}

pub struct CertificateRewriteRequest<'a> {
    pub genuine_leaf_der: &'a [u8],
    pub issuer_certificate_der: &'a [u8],
    pub issuer_private_key_pkcs8: &'a [u8],
    pub signing_algorithm: SigningAlgorithm,
    pub patch_levels: PatchLevels,
    pub id_overrides: &'a [AttestationIdOverride<'a>],
    pub module_hash: Option<&'a [u8]>,
    pub verified_boot_key: &'a [u8; 32],
    pub verified_boot_hash: &'a [u8; 32],
}

#[derive(Debug, Eq, PartialEq)]
pub struct CertificateRewriteResult {
    pub leaf_der: Vec<u8>,
    pub captured_patch_levels: CapturedPatchLevels,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Error {
    Bounds,
    InvalidCertificate,
    MissingAttestationExtension,
    DuplicateAttestationExtension,
    AttestationRewrite,
    InvalidPrivateKey,
    IssuerKeyMismatch,
    Signature,
    Encoding,
}

impl fmt::Display for Error {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(match self {
            Self::Bounds => "certificate rewrite input exceeds a bounded limit",
            Self::InvalidCertificate => "certificate DER is invalid",
            Self::MissingAttestationExtension => "Android attestation extension is missing",
            Self::DuplicateAttestationExtension => "Android attestation extension is duplicated",
            Self::AttestationRewrite => "Android attestation extension rewrite failed",
            Self::InvalidPrivateKey => "issuer private key is invalid",
            Self::IssuerKeyMismatch => "issuer private key does not match issuer certificate",
            Self::Signature => "certificate signature failed",
            Self::Encoding => "certificate DER encoding failed",
        })
    }
}

impl std::error::Error for Error {}

pub fn rewrite_certificate(
    request: &CertificateRewriteRequest<'_>,
) -> Result<CertificateRewriteResult, Error> {
    validate_bounds(request)?;
    let mut leaf =
        Certificate::from_der(request.genuine_leaf_der).map_err(|_| Error::InvalidCertificate)?;
    let issuer = Certificate::from_der(request.issuer_certificate_der)
        .map_err(|_| Error::InvalidCertificate)?;

    let extensions = leaf
        .tbs_certificate
        .extensions
        .as_mut()
        .ok_or(Error::MissingAttestationExtension)?;
    let mut matching_index = None;
    for (index, extension) in extensions.iter().enumerate() {
        if extension.extn_id == ANDROID_ATTESTATION_OID && matching_index.replace(index).is_some() {
            return Err(Error::DuplicateAttestationExtension);
        }
    }
    let index = matching_index.ok_or(Error::MissingAttestationExtension)?;
    let rewritten = rewrite_extension(&RewriteRequest {
        extension_der: extensions[index].extn_value.as_bytes(),
        patch_levels: request.patch_levels,
        id_overrides: request.id_overrides,
        module_hash: request.module_hash,
        verified_boot_key: request.verified_boot_key,
        verified_boot_hash: request.verified_boot_hash,
    })
    .map_err(|_| Error::AttestationRewrite)?;
    extensions[index].critical = false;
    extensions[index].extn_value =
        OctetString::new(rewritten.extension_der).map_err(|_| Error::Encoding)?;

    // Match the managed X509v3CertificateBuilder oracle: rebuild from the genuine serial,
    // validity, subject, SPKI and extensions. The builder does not carry issuer/subject unique IDs.
    leaf.tbs_certificate.issuer = issuer.tbs_certificate.subject.clone();
    leaf.tbs_certificate.issuer_unique_id = None;
    leaf.tbs_certificate.subject_unique_id = None;

    let leaf_der = match request.signing_algorithm {
        SigningAlgorithm::EcP256Sha256 => sign_ec(
            leaf.tbs_certificate,
            &issuer,
            request.issuer_private_key_pkcs8,
        )?,
        SigningAlgorithm::RsaPkcs1Sha256 => sign_rsa(
            leaf.tbs_certificate,
            &issuer,
            request.issuer_private_key_pkcs8,
        )?,
    };
    if leaf_der.len() > MAX_CERTIFICATE_DER_BYTES {
        return Err(Error::Bounds);
    }
    Ok(CertificateRewriteResult {
        leaf_der,
        captured_patch_levels: rewritten.captured_patch_levels,
    })
}

fn validate_bounds(request: &CertificateRewriteRequest<'_>) -> Result<(), Error> {
    if request.genuine_leaf_der.is_empty()
        || request.genuine_leaf_der.len() > MAX_CERTIFICATE_DER_BYTES
        || request.issuer_certificate_der.is_empty()
        || request.issuer_certificate_der.len() > MAX_CERTIFICATE_DER_BYTES
        || request.issuer_private_key_pkcs8.is_empty()
        || request.issuer_private_key_pkcs8.len() > MAX_PRIVATE_KEY_DER_BYTES
    {
        return Err(Error::Bounds);
    }
    Ok(())
}

fn sign_ec(
    mut tbs: TbsCertificate,
    issuer: &Certificate,
    private_key_pkcs8: &[u8],
) -> Result<Vec<u8>, Error> {
    let signer =
        EcSigningKey::from_pkcs8_der(private_key_pkcs8).map_err(|_| Error::InvalidPrivateKey)?;
    let algorithm = signer
        .signature_algorithm_identifier()
        .map_err(|_| Error::Encoding)?;
    tbs.signature = algorithm.clone();
    let tbs_der = tbs.to_der().map_err(|_| Error::Encoding)?;
    let signature: EcSignature = signer.try_sign(&tbs_der).map_err(|_| Error::Signature)?;

    let issuer_spki = issuer
        .tbs_certificate
        .subject_public_key_info
        .to_der()
        .map_err(|_| Error::Encoding)?;
    let verifier =
        EcVerifyingKey::from_public_key_der(&issuer_spki).map_err(|_| Error::IssuerKeyMismatch)?;
    verifier
        .verify(&tbs_der, &signature)
        .map_err(|_| Error::IssuerKeyMismatch)?;

    let signature_der = signature.to_der();
    encode_certificate(
        tbs,
        algorithm,
        BitString::from_bytes(signature_der.as_bytes()).map_err(|_| Error::Encoding)?,
    )
}

fn sign_rsa(
    mut tbs: TbsCertificate,
    issuer: &Certificate,
    private_key_pkcs8: &[u8],
) -> Result<Vec<u8>, Error> {
    let signer = RsaSigningKey::<Sha256>::from_pkcs8_der(private_key_pkcs8)
        .map_err(|_| Error::InvalidPrivateKey)?;
    let algorithm = signer
        .signature_algorithm_identifier()
        .map_err(|_| Error::Encoding)?;
    tbs.signature = algorithm.clone();
    let tbs_der = tbs.to_der().map_err(|_| Error::Encoding)?;
    let signature: RsaSignature = signer.try_sign(&tbs_der).map_err(|_| Error::Signature)?;

    let issuer_spki = issuer
        .tbs_certificate
        .subject_public_key_info
        .to_der()
        .map_err(|_| Error::Encoding)?;
    let issuer_public = rsa::RsaPublicKey::from_public_key_der(&issuer_spki)
        .map_err(|_| Error::IssuerKeyMismatch)?;
    let verifier = RsaVerifyingKey::<Sha256>::new(issuer_public);
    verifier
        .verify(&tbs_der, &signature)
        .map_err(|_| Error::IssuerKeyMismatch)?;

    let signature_bytes = signature.to_vec();
    encode_certificate(
        tbs,
        algorithm,
        BitString::from_bytes(&signature_bytes).map_err(|_| Error::Encoding)?,
    )
}

fn encode_certificate(
    tbs_certificate: TbsCertificate,
    signature_algorithm: x509_cert::spki::AlgorithmIdentifierOwned,
    signature: BitString,
) -> Result<Vec<u8>, Error> {
    Certificate {
        tbs_certificate,
        signature_algorithm,
        signature,
    }
    .to_der()
    .map_err(|_| Error::Encoding)
}
