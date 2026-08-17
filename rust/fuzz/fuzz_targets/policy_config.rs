#![no_main]

use cleverestricky_policy_config_core::{parse_app_config, parse_target_packages};
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let _ = parse_app_config(data);
    let _ = parse_target_packages(data);
});
