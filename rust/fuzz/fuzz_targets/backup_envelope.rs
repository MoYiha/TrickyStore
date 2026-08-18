#![no_main]

use cleverestricky_crypto_core::decrypt_backup;
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let _ = decrypt_backup(data.to_vec(), "fuzz-backup-password");
});
