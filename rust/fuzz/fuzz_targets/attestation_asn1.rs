#![no_main]

use cleverestricky_attestation_core::{PatchComponent, PatchLevels, RewriteRequest, rewrite_extension};
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let boot_key = [0x11u8; 32];
    let boot_hash = [0x22u8; 32];
    let _ = rewrite_extension(&RewriteRequest {
        extension_der: data,
        patch_levels: PatchLevels {
            system: PatchComponent::KEEP,
            vendor: PatchComponent::KEEP,
            boot: PatchComponent::KEEP,
        },
        id_overrides: &[],
        module_hash: None,
        verified_boot_key: &boot_key,
        verified_boot_hash: &boot_hash,
    });
});
