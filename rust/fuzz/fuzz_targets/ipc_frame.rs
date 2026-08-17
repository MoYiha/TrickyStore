// Additional GPLv3 section 7(b) attribution term for tryigit-owned material: see ../../../NOTICE.
#![no_main]

use cleverestricky_service_core::ipc::{read_frame_into, MAX_FRAME_BYTES};
use libfuzzer_sys::fuzz_target;
use std::io::Cursor;

fuzz_target!(|data: &[u8]| {
    let mut reader = Cursor::new(data);
    let mut scratch = [0u8; 4096];
    let _ = read_frame_into(&mut reader, &mut scratch);

    // Exercise the configured payload ceiling without allocating from the input-controlled length.
    if data.len() >= 16 {
        let mut bounded_reader = Cursor::new(data);
        let mut zero_scratch = [];
        let _ = cleverestricky_service_core::ipc::read_header_bounded(
            &mut bounded_reader,
            MAX_FRAME_BYTES,
        );
        let _ = read_frame_into(&mut Cursor::new(data), &mut zero_scratch);
    }
});
