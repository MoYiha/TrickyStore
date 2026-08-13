/* C ABI for the Rust helpers used by the native Binder interceptor. */
#ifndef CLEVERESTRICKY_NATIVE_CORE_H
#define CLEVERESTRICKY_NATIVE_CORE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    size_t target_ptr_offset;
    size_t cookie_offset;
    size_t code_offset;
    size_t flags_offset;
    size_t sender_pid_offset;
    size_t sender_euid_offset;
    size_t data_size_offset;
    size_t data_ptr_offset;
    size_t transaction_data_size;
    size_t transaction_data_secctx_size;
    size_t bwr_write_size_offset;
    size_t bwr_write_consumed_offset;
    size_t bwr_write_buffer_offset;
    size_t bwr_read_size_offset;
    size_t bwr_read_consumed_offset;
    size_t bwr_read_buffer_offset;
    size_t bwr_total_size;
    uint8_t valid;
} RustOffsetCacheView;

typedef struct {
    uintptr_t target_ptr;
    uintptr_t cookie;
    uint32_t code;
    uint32_t flags;
    int32_t sender_pid;
    uint32_t sender_euid;
    uint64_t data_size;
    uintptr_t data_buffer;
    uint32_t cmd;
    uintptr_t raw_ptr;
    size_t raw_size;
    uint8_t valid;
} RustParsedTransaction;

typedef struct {
    uintptr_t read_size;
    uintptr_t read_consumed;
    uintptr_t read_buffer;
    uint8_t valid;
} RustBinderReadSnapshot;

bool rust_parse_binder_stream(const uint8_t *buffer,
                              size_t consumed,
                              size_t buffer_size,
                              const RustOffsetCacheView *cache,
                              RustParsedTransaction *transactions,
                              size_t transaction_capacity,
                              size_t *transaction_count);

bool rust_validate_offset_cache(const RustOffsetCacheView *cache);

bool rust_validate_binder_probe(const uint8_t *buffer,
                                size_t length,
                                size_t transaction_size);
bool rust_read_binder_write_read(const uint8_t *input,
                                 const RustOffsetCacheView *cache,
                                 RustBinderReadSnapshot *output);
bool rust_write_binder_transaction(uint8_t *buffer,
                                   size_t consumed,
                                   const RustParsedTransaction *transaction,
                                   const RustOffsetCacheView *cache);

bool rust_is_binder_fd_after_successful_ioctl(int32_t descriptor,
                                              uintptr_t exchange_token);

int32_t rust_parse_android_api_level(const uint8_t *value, size_t length);

bool rust_parse_kernel_release(const uint8_t *value,
                               size_t length,
                               int32_t *major,
                               int32_t *minor);

#ifdef __cplusplus
}
#endif

#endif
