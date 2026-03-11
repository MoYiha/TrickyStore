#ifndef BINDER_INTERCEPTOR_H
#define BINDER_INTERCEPTOR_H

#include <utils/RefBase.h>
#include <binder/Parcel.h>
#include <binder/IBinder.h>
#include <binder/Binder.h>
#include <utils/StrongPointer.h>
#include <map>
#include <unordered_map>
#include <shared_mutex>
#include <cstdint>
#include <cstddef>
#include <mutex>
#include <atomic>
#include <string>

using namespace android;

// =============================================================================
// Adaptive Binder Interceptor Architecture
//
// This header defines a fully dynamic, version-immune Binder interception
// framework. Instead of hardcoding kernel struct layouts, it discovers
// offsets at runtime through three layered strategies:
//
//   1. BTF Kernel Introspection  (kernel 5.4+)
//   2. Runtime Heuristic Probing (dummy PING_TRANSACTION)
//   3. Multi-Version Fallback Database (Android 8 - 15+)
//
// A state-machine stream parser processes the binder read buffer, and all
// memory accesses go through bounds-checked safe accessors.
// =============================================================================

// ---------------------------------------------------------------------------
// OffsetCache — Singleton that holds dynamically discovered struct offsets.
// Populated once during module init; read-only afterward.
// ---------------------------------------------------------------------------
struct OffsetCache {
    // binder_transaction_data field offsets (bytes from struct start)
    size_t target_ptr_offset  = 0;   // offset of target.ptr union member
    size_t cookie_offset      = 0;   // offset of cookie field
    size_t code_offset        = 0;   // offset of code field
    size_t flags_offset       = 0;   // offset of flags field
    size_t sender_pid_offset  = 0;   // offset of sender_pid
    size_t sender_euid_offset = 0;   // offset of sender_euid
    size_t data_size_offset   = 0;   // offset of data_size
    size_t data_ptr_offset    = 0;   // offset of data.ptr.buffer

    // Total struct sizes (discovered dynamically, never hardcoded)
    size_t transaction_data_size         = 0;
    size_t transaction_data_secctx_size  = 0;

    // binder_write_read field offsets
    size_t bwr_write_size_offset    = 0;
    size_t bwr_write_consumed_offset = 0;
    size_t bwr_write_buffer_offset  = 0;
    size_t bwr_read_size_offset     = 0;
    size_t bwr_read_consumed_offset = 0;
    size_t bwr_read_buffer_offset   = 0;
    size_t bwr_total_size           = 0;

    // Validation & state
    bool   valid         = false;
    bool   btf_source    = false;    // true if offsets came from BTF
    bool   heuristic_source = false; // true if offsets came from heuristic
    bool   fallback_mode = false;    // true if using static fallback

    int    android_api_level = 0;
    std::string kernel_version;

    // Singleton accessor
    static OffsetCache& instance();

    // Sanity-check the discovered offsets
    bool validateOffsets() const;
};

// ---------------------------------------------------------------------------
// BtfProvider — Reads BTF (BPF Type Format) data from the running kernel
// to discover struct field offsets without any compile-time assumptions.
// Available on kernel 5.4+ with CONFIG_DEBUG_INFO_BTF=y.
// ---------------------------------------------------------------------------
class BtfProvider {
public:
    // Returns true if BTF data is available on this kernel
    static bool isAvailable();

    // Query the byte offset of a named field within a named struct.
    // Returns -1 on failure.
    static int queryStructLayout(const char *struct_name, const char *field_name);

    // Query the total size of a named struct. Returns 0 on failure.
    static size_t queryStructSize(const char *struct_name);

    // Populate the OffsetCache from BTF data. Returns true on success.
    static bool populateCache(OffsetCache &cache);

private:
    static bool readBtf(const char *path, const char *struct_name,
                        const char *field_name, int &out_offset);
};

// ---------------------------------------------------------------------------
// RuntimeOffsetDiscovery — Sends a dummy PING_TRANSACTION through the binder
// driver and observes memory patterns to discover struct field positions.
// ---------------------------------------------------------------------------
class RuntimeOffsetDiscovery {
public:
    // Run the heuristic offset discovery. Returns true if offsets were found.
    static bool discoverOffsets(OffsetCache &cache);

    // Probe a specific struct by sending a controlled transaction and
    // analyzing the returned bytes for known marker patterns.
    static bool probeOffsets(OffsetCache &cache);

private:
    // Send a PING_TRANSACTION to servicemanager and capture the raw response
    static bool sendPingProbe(uint8_t *out_buf, size_t buf_size, size_t &out_len);

    // Scan a captured buffer for known binder command patterns
    static bool analyzeProbeResult(const uint8_t *buf, size_t len,
                                   OffsetCache &cache);
};

// ---------------------------------------------------------------------------
// FallbackDatabase — Static offset maps for known Android/Kernel combinations.
// Indexed by (API level, kernel major, kernel minor).
// ---------------------------------------------------------------------------
struct FallbackOffsetEntry {
    int    api_level;
    int    kernel_major;
    int    kernel_minor;
    size_t transaction_data_size;
    size_t secctx_size;
    size_t target_ptr_offset;
    size_t cookie_offset;
    size_t code_offset;
    size_t flags_offset;
    size_t sender_pid_offset;
    size_t sender_euid_offset;
    size_t data_size_offset;
    size_t data_ptr_offset;
    size_t bwr_total_size;
};

class FallbackDatabase {
public:
    // Lookup the best matching entry for the current device.
    // Returns true and populates cache on success.
    static bool lookup(int api_level, int kernel_major, int kernel_minor,
                       OffsetCache &cache);

    // Get the full table (for testing / enumeration)
    static const FallbackOffsetEntry* getTable(size_t &out_count);

private:
    static const FallbackOffsetEntry s_entries[];
    static const size_t s_entry_count;
};

// ---------------------------------------------------------------------------
// BinderStreamParser — State-machine that processes ioctl read buffers.
// Parses binder driver return commands without assuming fixed struct sizes.
// ---------------------------------------------------------------------------
class BinderStreamParser {
public:
    // Parser states
    enum class ParserState {
        PARSE_CMD,        // Reading the next 4-byte command
        PARSE_PAYLOAD,    // Reading command payload based on _IOC_SIZE
        DONE,             // No more data
        ERROR             // Unrecoverable parse error
    };

    struct ParsedTransaction {
        uintptr_t target_ptr;
        uintptr_t cookie;
        uint32_t  code;
        uint32_t  flags;
        int32_t   sender_pid;
        uint32_t  sender_euid;
        uint64_t  data_size;
        uintptr_t data_buffer;
        uint32_t  cmd;           // BR_TRANSACTION or BR_TRANSACTION_SEC_CTX
        uintptr_t raw_ptr;      // pointer to start of transaction_data in buffer
        size_t    raw_size;     // size of transaction_data region
        bool      valid;
    };

    // Parse the binder read buffer and extract transactions.
    // Uses the OffsetCache for field positions. All reads are bounds-checked.
    static bool parse(uintptr_t buffer, size_t consumed, size_t buffer_size,
                      const OffsetCache &cache,
                      ParsedTransaction *out_txns, size_t max_txns,
                      size_t &out_txn_count);

    // Write back a modified transaction into the buffer (bounds-checked).
    static bool writeBack(uintptr_t buffer_ptr, const ParsedTransaction &txn,
                          const OffsetCache &cache);

private:
    // Safe read helpers with bounds checking
    static bool safeRead(uintptr_t base, size_t offset, void *dst,
                         size_t len, size_t buffer_end);
    static bool safeWrite(uintptr_t base, size_t offset, const void *src,
                          size_t len, size_t buffer_end);
};

// ---------------------------------------------------------------------------
// AdaptiveBinderInterceptor — Top-level orchestrator. Initializes the offset
// discovery pipeline (BTF → Heuristic → Fallback) and wires up the
// state-machine parser for all ioctl interception.
// ---------------------------------------------------------------------------
class AdaptiveBinderInterceptor {
public:
    // Initialize the adaptive system: detect versions, discover offsets.
    // Must be called once during module load before hooks are active.
    bool initialize();

    // Get the active OffsetCache (read-only after init)
    const OffsetCache& getCache() const { return OffsetCache::instance(); }

    // Detect Android API level from system properties
    static int detectApiLevel();

    // Parse kernel version string into (major, minor)
    static bool parseKernelVersion(int &major, int &minor);

private:
    bool initBtf(OffsetCache &cache);
    bool initHeuristic(OffsetCache &cache);
    bool initFallback(OffsetCache &cache);

    std::once_flag init_flag_;
    bool initialized_ = false;
};

// ---------------------------------------------------------------------------
// DeviceRecallProtection — Countermeasures against Google Play Integrity API's
// Device Recall feature (beta, 2026). Device Recall stores 3 persistent bits
// per device per developer account on Google's servers, surviving factory
// resets. This class detects and neutralizes device recall signals.
//
// Defense strategy:
//   1. Detect integrity token warmup/request Binder transactions
//   2. Randomize device identity signals sent to GMS Core
//   3. Interfere with device recall bit propagation in verdicts
//   4. Coordinate with DRM ID / build prop randomization
// ---------------------------------------------------------------------------
class DeviceRecallProtection {
public:
    // Initialize the protection layer. Reads config to decide behavior.
    static bool initialize();

    // Check if a Binder transaction descriptor matches a Play Integrity service
    static bool isIntegrityServiceDescriptor(const char *descriptor, size_t len);

    // Check if a transaction code is a known integrity API warmup/request
    static bool isRecallRelatedTransaction(uint32_t code,
                                           const char *descriptor,
                                           size_t desc_len);

    // Mutate device identity signals before they reach GMS integrity checks.
    // Called before integrity token generation to ensure device appears "new".
    static void randomizeDeviceSignals();

    // Get the current protection state
    static bool isEnabled();

    // Service descriptors that handle Play Integrity
    static constexpr const char *INTEGRITY_SERVICE_DESCRIPTOR =
        "com.google.android.play.core.integrity";
    static constexpr const char *GMS_INTEGRITY_DESCRIPTOR =
        "com.google.android.gms.playintegrity";
    static constexpr const char *DEVICE_RECALL_INDICATOR =
        "deviceRecall";

    // Known transaction codes for Play Integrity warmup/request
    static constexpr uint32_t INTEGRITY_WARMUP_CODE   = 1;
    static constexpr uint32_t INTEGRITY_REQUEST_CODE   = 2;
    static constexpr uint32_t INTEGRITY_STANDARD_CODE  = 3;

private:
    static std::atomic<bool> s_enabled;
    static std::atomic<bool> s_initialized;

    // Detect if device recall config file exists
    static bool readConfig();
};

// ---------------------------------------------------------------------------
// BinderInterceptor — The Binder-level intercept handler (unchanged API).
// Manages registered interceptor items and dispatches pre/post transact.
// ---------------------------------------------------------------------------
struct WpIBinderHash {
    std::size_t operator()(const wp<IBinder>& ptr) const {
        return std::hash<IBinder*>()(ptr.unsafe_get());
    }
};

struct WpIBinderEqual {
    bool operator()(const wp<IBinder>& lhs, const wp<IBinder>& rhs) const {
        return lhs == rhs;
    }
};

class BinderInterceptor : public BBinder {
public:
    sp<IBinder> gPropertyServiceBinder = nullptr;

    bool handleIntercept(sp<BBinder> target, uint32_t code, const Parcel &data,
                         Parcel *reply, uint32_t flags, status_t &result);
    bool needIntercept(const wp<BBinder>& target);

    status_t onTransact(uint32_t code, const android::Parcel &data,
                        android::Parcel *reply, uint32_t flags) override;

private:
    enum {
        REGISTER_INTERCEPTOR = 1,
        UNREGISTER_INTERCEPTOR = 2,
        REGISTER_PROPERTY_SERVICE = 3
    };
    enum {
        PRE_TRANSACT = 1,
        POST_TRANSACT,
        INTERCEPTOR_REPLACED = 3
    };
    enum {
        SKIP = 1,
        CONTINUE,
        OVERRIDE_REPLY,
        OVERRIDE_DATA
    };

    struct InterceptItem {
        wp<IBinder> target{};
        sp<IBinder> interceptor;
    };

    using RwLock = std::shared_mutex;
    using WriteGuard = std::unique_lock<RwLock>;
    using ReadGuard = std::shared_lock<RwLock>;
    RwLock lock;
    std::unordered_map<wp<IBinder>, InterceptItem, WpIBinderHash, WpIBinderEqual> items{};
};

#endif // BINDER_INTERCEPTOR_H
