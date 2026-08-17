#![no_main]

use cleverestricky_cbox_recovery_core::decrypt_cbox_with_recovery_key;
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let recovery_key = [0x5au8; 32];
    let _ = decrypt_cbox_with_recovery_key(data.to_vec(), &recovery_key);
});
