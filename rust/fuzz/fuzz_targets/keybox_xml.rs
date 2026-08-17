// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../../NOTICE.
#![no_main]

use cleverestricky_xml_core::parse_keybox_xml_bytes;
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let _ = parse_keybox_xml_bytes(data);
});
