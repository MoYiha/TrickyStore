#![no_main]

use cleverestricky_backend::fuzz_certificate_wire;
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    fuzz_certificate_wire(data);
});
