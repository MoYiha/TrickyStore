use crate::ffi::validate_slice_args;
use std::mem;

#[derive(Clone, Copy, Debug, Default)]
#[repr(C)]
pub struct RustOffsetCacheView {
    pub target_ptr_offset: usize,
    pub cookie_offset: usize,
    pub code_offset: usize,
    pub flags_offset: usize,
    pub sender_pid_offset: usize,
    pub sender_euid_offset: usize,
    pub data_size_offset: usize,
    pub data_ptr_offset: usize,
    pub transaction_data_size: usize,
    pub transaction_data_secctx_size: usize,
    pub bwr_write_size_offset: usize,
    pub bwr_write_consumed_offset: usize,
    pub bwr_write_buffer_offset: usize,
    pub bwr_read_size_offset: usize,
    pub bwr_read_consumed_offset: usize,
    pub bwr_read_buffer_offset: usize,
    pub bwr_total_size: usize,
    pub valid: u8,
}

fn fits(offset: usize, width: usize, total: usize) -> bool {
    offset <= total && width <= total.saturating_sub(offset)
}

fn ordered_non_overlapping(fields: &[(usize, usize)], total: usize) -> bool {
    fields
        .iter()
        .all(|(offset, width)| fits(*offset, *width, total))
        && fields.windows(2).all(|pair| {
            pair[0]
                .0
                .checked_add(pair[0].1)
                .is_some_and(|end| end <= pair[1].0)
        })
}

pub fn validate_transaction_layout(cache: &RustOffsetCacheView) -> bool {
    if cache.valid == 0 || !(40..=512).contains(&cache.transaction_data_size) {
        return false;
    }
    if cache.transaction_data_secctx_size < cache.transaction_data_size
        || cache.transaction_data_secctx_size > 512
    {
        return false;
    }

    let pointer_width = mem::size_of::<usize>();
    ordered_non_overlapping(
        &[
            (cache.target_ptr_offset, pointer_width),
            (cache.cookie_offset, pointer_width),
            (cache.code_offset, mem::size_of::<u32>()),
            (cache.flags_offset, mem::size_of::<u32>()),
            (cache.sender_pid_offset, mem::size_of::<i32>()),
            (cache.sender_euid_offset, mem::size_of::<u32>()),
            (cache.data_size_offset, pointer_width),
            (cache.data_ptr_offset, pointer_width),
        ],
        cache.transaction_data_size,
    )
}

pub fn validate_offset_cache(cache: &RustOffsetCacheView) -> bool {
    if !validate_transaction_layout(cache) || !(24..=256).contains(&cache.bwr_total_size) {
        return false;
    }

    let width = mem::size_of::<usize>();
    ordered_non_overlapping(
        &[
            (cache.bwr_write_size_offset, width),
            (cache.bwr_write_consumed_offset, width),
            (cache.bwr_write_buffer_offset, width),
            (cache.bwr_read_size_offset, width),
            (cache.bwr_read_consumed_offset, width),
            (cache.bwr_read_buffer_offset, width),
        ],
        cache.bwr_total_size,
    )
}

#[no_mangle]
/// Validates the complete Binder layout supplied by the native bridge.
///
/// # Safety
/// `cache_pointer` must point to one initialized `RustOffsetCacheView` that
/// remains readable for the duration of the call.
pub unsafe extern "C" fn rust_validate_offset_cache(
    cache_pointer: *const RustOffsetCacheView,
) -> bool {
    std::panic::catch_unwind(|| {
        let cache = match unsafe { validate_slice_args(cache_pointer, 1) } {
            Some(value) => &value[0],
            None => return false,
        };
        validate_offset_cache(cache)
    })
    .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn valid_layout() -> RustOffsetCacheView {
        RustOffsetCacheView {
            target_ptr_offset: 0,
            cookie_offset: 8,
            code_offset: 16,
            flags_offset: 20,
            sender_pid_offset: 24,
            sender_euid_offset: 28,
            data_size_offset: 40,
            data_ptr_offset: 48,
            transaction_data_size: 64,
            transaction_data_secctx_size: 72,
            bwr_write_size_offset: 0,
            bwr_write_consumed_offset: 8,
            bwr_write_buffer_offset: 16,
            bwr_read_size_offset: 24,
            bwr_read_consumed_offset: 32,
            bwr_read_buffer_offset: 40,
            bwr_total_size: 48,
            valid: 1,
        }
    }

    #[test]
    fn accepts_a_complete_supported_layout() {
        assert!(validate_offset_cache(&valid_layout()));
    }

    #[test]
    fn rejects_overflowing_and_reordered_fields() {
        let mut layout = valid_layout();
        layout.data_ptr_offset = usize::MAX;
        assert!(!validate_offset_cache(&layout));

        layout = valid_layout();
        layout.cookie_offset = layout.target_ptr_offset;
        assert!(!validate_offset_cache(&layout));
    }

    #[test]
    fn rejects_an_invalid_binder_write_read_layout() {
        let mut layout = valid_layout();
        layout.bwr_read_buffer_offset = layout.bwr_total_size;
        assert!(!validate_offset_cache(&layout));

        layout = valid_layout();
        layout.bwr_read_consumed_offset = layout.bwr_read_size_offset;
        assert!(!validate_offset_cache(&layout));
    }
}
