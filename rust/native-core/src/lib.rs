//! Memory safe parsing and validation for the native Android bridge.
//!
//! Android C++ ABI calls remain in a narrow bridge. Rust owns bounded parsing,
//! layout validation, injection request policy, and ancillary data validation.

pub mod binder_memory;
pub mod binder_parser;
pub mod ffi;
pub mod injector_support;
pub mod layout;
pub mod platform;

#[cfg(test)]
mod abi_tests {
    use super::binder_memory::RustBinderReadSnapshot;
    use super::binder_parser::RustParsedTransaction;
    use super::layout::RustOffsetCacheView;
    use std::mem;

    #[test]
    fn native_bridge_structures_match_supported_64_bit_abis() {
        assert_eq!(mem::size_of::<usize>(), 8);
        assert_eq!(mem::size_of::<RustOffsetCacheView>(), 144);
        assert_eq!(mem::offset_of!(RustOffsetCacheView, valid), 136);
        assert_eq!(mem::size_of::<RustParsedTransaction>(), 80);
        assert_eq!(mem::offset_of!(RustParsedTransaction, raw_ptr), 56);
        assert_eq!(mem::offset_of!(RustParsedTransaction, valid), 72);
        assert_eq!(mem::size_of::<RustBinderReadSnapshot>(), 32);
        assert_eq!(mem::offset_of!(RustBinderReadSnapshot, valid), 24);
    }
}
