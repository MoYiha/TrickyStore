// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../NOTICE.
#![forbid(unsafe_code)]

mod certificate_wire;
mod crl_wire;
mod keybox_wire;

/// Fuzz-only entry point for the real certificate inspect/rewrite wire parser.
pub fn fuzz_certificate_wire(input: &[u8]) {
    let _ = certificate_wire::inspect_and_encode(input.to_vec());
    let _ = certificate_wire::rewrite_and_encode(input.to_vec());
}

/// Fuzz-only entry point for the real stateful CRL wire parser.
pub fn fuzz_crl_wire(input: &[u8]) {
    let _ = crl_wire::handle(input.to_vec());
}
