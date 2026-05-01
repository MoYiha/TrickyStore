// =============================================================================
// binder_abi.h — Dynamic Binder ABI Resolution via dlopen/dlsym
//
// Problem:
//   Directly including <binder/Parcel.h> and linking against a stub libbinder
//   created from AOSP headers hard-codes class sizes and vtable layouts.
//   OEM ROMs (HyperOS, OneUI, OPPO ColorOS, etc.) modify libbinder to add
//   vendor-specific fields, changing sizeof(Parcel), sizeof(BBinder), and
//   vtable offsets.  Using the wrong sizes causes heap corruption, crashes,
//   or silent data corruption.
//
// Solution:
//   This header provides a BinderAbi singleton that:
//     1. Opens the REAL libbinder.so from the running device via dlopen.
//     2. Resolves all required Parcel / IPCThreadState symbols via dlsym.
//     3. Exports thin inline wrappers so the rest of the code calls through
//        function pointers rather than compiled-in method calls.
//     4. On failure (symbol missing / OEM rename), returns safe error codes
//        instead of crashing.
//
// Usage:
//   // At module init, before any Parcel usage:
//   BinderAbi::initialize();
//
//   // Instead of: parcel.writeInt32(42)
//   BinderAbi::parcel_writeInt32(&parcel, 42);
//
//   // Instead of: parcel.readInt32()
//   int32_t v = BinderAbi::parcel_readInt32(&parcel);
// =============================================================================

#pragma once

#include <cstdint>
#include <dlfcn.h>
#include <utils/Errors.h>
#include "logging.hpp"

// Forward-declare to avoid pulling in the full Parcel.h in headers that only
// need the dynamic ABI layer.
namespace android { class Parcel; class IBinder; }
namespace android { template <typename T> class sp; }

// ---------------------------------------------------------------------------
// BinderAbi — Singleton holding all dynamically-resolved function pointers
// ---------------------------------------------------------------------------
class BinderAbi {
public:
    // -----------------------------------------------------------------------
    // init() — must be called once at module load, before any Binder usage.
    // Returns true if all critical symbols were resolved; false if some are
    // missing (the module will still run but with degraded functionality).
    // -----------------------------------------------------------------------
    static bool initialize() {
        Instance &inst = get();
        if (inst.initialized) return inst.all_ok;
        inst.initialized = true;

        // Try the canonical name first, then common vendor variants.
        const char *libs[] = {
            "libbinder.so",
            "/system/lib64/libbinder.so",
            "/system/lib/libbinder.so",
            nullptr
        };

        void *handle = nullptr;
        for (int i = 0; libs[i] != nullptr; ++i) {
            handle = dlopen(libs[i], RTLD_NOW | RTLD_NOLOAD | RTLD_GLOBAL);
            if (!handle) {
                handle = dlopen(libs[i], RTLD_NOW | RTLD_GLOBAL);
            }
            if (handle) {
                LOGI("BinderAbi: loaded %s", libs[i]);
                break;
            }
        }

        if (!handle) {
            LOGE("BinderAbi: FAILED to open libbinder.so — %s", dlerror());
            inst.all_ok = false;
            return false;
        }
        inst.lib_handle = handle;

        bool ok = true;

        // ---- Parcel primitives ----
        ok &= resolve(handle, inst.fn_parcel_writeInt32,
                      "_ZN7android6Parcel10writeInt32Ei");
        ok &= resolve(handle, inst.fn_parcel_writeUint32,
                      "_ZN7android6Parcel11writeUint32Ej");
        ok &= resolve(handle, inst.fn_parcel_writeInt64,
                      "_ZN7android6Parcel10writeInt64Ex");
        ok &= resolve(handle, inst.fn_parcel_writeUint64,
                      "_ZN7android6Parcel11writeUint64Ey");
        ok &= resolve(handle, inst.fn_parcel_write,
                      "_ZN7android6Parcel5writeEPKvj");
        ok &= resolve(handle, inst.fn_parcel_writeNoException,
                      "_ZN7android6Parcel15writeNoExceptionEv");
        ok &= resolve(handle, inst.fn_parcel_writeStrongBinder,
                      "_ZN7android6Parcel17writeStrongBinderERKNS_2spINS_7IBinderEEE");
        ok &= resolve(handle, inst.fn_parcel_freeData,
                      "_ZN7android6Parcel8freeDataEv");
        ok &= resolve(handle, inst.fn_parcel_appendFrom,
                      "_ZN7android6Parcel10appendFromEPKS0_jj");

        ok &= resolve(handle, inst.fn_parcel_readInt32_ptr,
                      "_ZNK7android6Parcel9readInt32EPi");
        ok &= resolve(handle, inst.fn_parcel_readInt32_val,
                      "_ZNK7android6Parcel9readInt32Ev");
        ok &= resolve(handle, inst.fn_parcel_readUint32_ptr,
                      "_ZNK7android6Parcel10readUint32EPj");
        ok &= resolve(handle, inst.fn_parcel_readUint64_val,
                      "_ZNK7android6Parcel10readUint64Ev");
        ok &= resolve(handle, inst.fn_parcel_read,
                      "_ZNK7android6Parcel4readEPvj");
        ok &= resolve(handle, inst.fn_parcel_readStrongBinder_ptr,
                      "_ZNK7android6Parcel16readStrongBinderEPNS_2spINS_7IBinderEEE");
        ok &= resolve(handle, inst.fn_parcel_dataSize,
                      "_ZNK7android6Parcel8dataSizeEv");
        ok &= resolve(handle, inst.fn_parcel_dataAvail,
                      "_ZNK7android6Parcel9dataAvailEv");
        ok &= resolve(handle, inst.fn_parcel_dataPosition,
                      "_ZNK7android6Parcel12dataPositionEv");
        ok &= resolve(handle, inst.fn_parcel_setDataPosition,
                      "_ZNK7android6Parcel15setDataPositionEj");

        // ---- IPCThreadState ----
        ok &= resolve(handle, inst.fn_ipc_self,
                      "_ZN7android14IPCThreadState4selfEv");
        ok &= resolve(handle, inst.fn_ipc_getCallingUid,
                      "_ZNK7android14IPCThreadState13getCallingUidEv");
        ok &= resolve(handle, inst.fn_ipc_getCallingPid,
                      "_ZNK7android14IPCThreadState13getCallingPidEv");

        inst.all_ok = ok;
        if (ok) {
            LOGI("BinderAbi: all symbols resolved — dynamic ABI layer active");
        } else {
            LOGW("BinderAbi: some symbols missing — partial dynamic ABI, "
                 "falling back to static stubs where necessary");
        }
        return ok;
    }

    static bool isReady() { return get().initialized && get().lib_handle != nullptr; }

    // -----------------------------------------------------------------------
    // Thin wrappers — call the function pointer if available; return an error
    // code / default value otherwise.  All wrappers are safe to call even
    // when initialize() has not been called yet (they will just return early).
    // -----------------------------------------------------------------------

    static android::status_t parcel_writeInt32(android::Parcel *p, int32_t v) {
        if (get().fn_parcel_writeInt32) return get().fn_parcel_writeInt32(p, v);
        return android::INVALID_OPERATION;
    }
    static android::status_t parcel_writeUint32(android::Parcel *p, uint32_t v) {
        if (get().fn_parcel_writeUint32) return get().fn_parcel_writeUint32(p, v);
        return android::INVALID_OPERATION;
    }
    static android::status_t parcel_writeInt64(android::Parcel *p, int64_t v) {
        if (get().fn_parcel_writeInt64) return get().fn_parcel_writeInt64(p, v);
        return android::INVALID_OPERATION;
    }
    static android::status_t parcel_writeUint64(android::Parcel *p, uint64_t v) {
        if (get().fn_parcel_writeUint64) return get().fn_parcel_writeUint64(p, v);
        return android::INVALID_OPERATION;
    }
    static android::status_t parcel_write(android::Parcel *p, const void *data, uint32_t len) {
        if (get().fn_parcel_write) return get().fn_parcel_write(p, data, len);
        return android::INVALID_OPERATION;
    }
    static android::status_t parcel_writeNoException(android::Parcel *p) {
        if (get().fn_parcel_writeNoException) return get().fn_parcel_writeNoException(p);
        return android::INVALID_OPERATION;
    }
    static android::status_t parcel_writeStrongBinder(android::Parcel *p,
                                                       const android::sp<android::IBinder> &val) {
        if (get().fn_parcel_writeStrongBinder) return get().fn_parcel_writeStrongBinder(p, val);
        return android::INVALID_OPERATION;
    }
    static void parcel_freeData(android::Parcel *p) {
        if (get().fn_parcel_freeData) get().fn_parcel_freeData(p);
    }
    static android::status_t parcel_appendFrom(android::Parcel *p,
                                                const android::Parcel *other,
                                                uint32_t start, uint32_t len) {
        if (get().fn_parcel_appendFrom) return get().fn_parcel_appendFrom(p, other, start, len);
        return android::INVALID_OPERATION;
    }

    static android::status_t parcel_readInt32(const android::Parcel *p, int32_t *out) {
        if (get().fn_parcel_readInt32_ptr) return get().fn_parcel_readInt32_ptr(p, out);
        return android::INVALID_OPERATION;
    }
    static int32_t parcel_readInt32(const android::Parcel *p) {
        if (get().fn_parcel_readInt32_val) return get().fn_parcel_readInt32_val(p);
        return 0;
    }
    static android::status_t parcel_readUint32(const android::Parcel *p, uint32_t *out) {
        if (get().fn_parcel_readUint32_ptr) return get().fn_parcel_readUint32_ptr(p, out);
        return android::INVALID_OPERATION;
    }
    static uint64_t parcel_readUint64(const android::Parcel *p) {
        if (get().fn_parcel_readUint64_val) return get().fn_parcel_readUint64_val(p);
        return 0;
    }
    static android::status_t parcel_read(const android::Parcel *p, void *out, uint32_t len) {
        if (get().fn_parcel_read) return get().fn_parcel_read(p, out, len);
        return android::INVALID_OPERATION;
    }
    static android::status_t parcel_readStrongBinder(const android::Parcel *p,
                                                      android::sp<android::IBinder> *out) {
        if (get().fn_parcel_readStrongBinder_ptr) return get().fn_parcel_readStrongBinder_ptr(p, out);
        return android::INVALID_OPERATION;
    }
    static size_t parcel_dataSize(const android::Parcel *p) {
        if (get().fn_parcel_dataSize) return get().fn_parcel_dataSize(p);
        return 0;
    }
    static size_t parcel_dataAvail(const android::Parcel *p) {
        if (get().fn_parcel_dataAvail) return get().fn_parcel_dataAvail(p);
        return 0;
    }
    static size_t parcel_dataPosition(const android::Parcel *p) {
        if (get().fn_parcel_dataPosition) return get().fn_parcel_dataPosition(p);
        return 0;
    }
    static void parcel_setDataPosition(android::Parcel *p, uint32_t pos) {
        if (get().fn_parcel_setDataPosition) get().fn_parcel_setDataPosition(p, pos);
    }

    // -----------------------------------------------------------------------
    // IPCThreadState wrappers
    // -----------------------------------------------------------------------
    using IpcState = void; // opaque pointer
    static IpcState *ipc_self() {
        if (get().fn_ipc_self) return get().fn_ipc_self();
        return nullptr;
    }
    static uid_t ipc_getCallingUid(IpcState *st) {
        if (get().fn_ipc_getCallingUid && st) return get().fn_ipc_getCallingUid(st);
        return static_cast<uid_t>(-1);
    }
    static pid_t ipc_getCallingPid(IpcState *st) {
        if (get().fn_ipc_getCallingPid && st) return get().fn_ipc_getCallingPid(st);
        return -1;
    }

    // Convenience: returns calling UID via dynamic IPCThreadState::self()
    static uid_t getCallingUid() { return ipc_getCallingUid(ipc_self()); }
    static pid_t getCallingPid() { return ipc_getCallingPid(ipc_self()); }

private:
    // -----------------------------------------------------------------------
    // Internal instance — holds all function pointers and the dlopen handle
    // -----------------------------------------------------------------------
    struct Instance {
        bool   initialized = false;
        bool   all_ok      = false;
        void  *lib_handle  = nullptr;

        // Parcel write
        android::status_t (*fn_parcel_writeInt32)(android::Parcel *, int32_t)                   = nullptr;
        android::status_t (*fn_parcel_writeUint32)(android::Parcel *, uint32_t)                  = nullptr;
        android::status_t (*fn_parcel_writeInt64)(android::Parcel *, int64_t)                    = nullptr;
        android::status_t (*fn_parcel_writeUint64)(android::Parcel *, uint64_t)                  = nullptr;
        android::status_t (*fn_parcel_write)(android::Parcel *, const void *, uint32_t)          = nullptr;
        android::status_t (*fn_parcel_writeNoException)(android::Parcel *)                        = nullptr;
        android::status_t (*fn_parcel_writeStrongBinder)(android::Parcel *,
                                                          const android::sp<android::IBinder> &)           = nullptr;
        void              (*fn_parcel_freeData)(android::Parcel *)                                = nullptr;
        android::status_t (*fn_parcel_appendFrom)(android::Parcel *,
                                                   const android::Parcel *, uint32_t, uint32_t)  = nullptr;
        // Parcel read
        android::status_t (*fn_parcel_readInt32_ptr)(const android::Parcel *, int32_t *)         = nullptr;
        int32_t           (*fn_parcel_readInt32_val)(const android::Parcel *)                     = nullptr;
        android::status_t (*fn_parcel_readUint32_ptr)(const android::Parcel *, uint32_t *)       = nullptr;
        uint64_t          (*fn_parcel_readUint64_val)(const android::Parcel *)                    = nullptr;
        android::status_t (*fn_parcel_read)(const android::Parcel *, void *, uint32_t)           = nullptr;
        android::status_t (*fn_parcel_readStrongBinder_ptr)(const android::Parcel *,
                                                              android::sp<android::IBinder> *)             = nullptr;
        size_t            (*fn_parcel_dataSize)(const android::Parcel *)                          = nullptr;
        size_t            (*fn_parcel_dataAvail)(const android::Parcel *)                         = nullptr;
        size_t            (*fn_parcel_dataPosition)(const android::Parcel *)                      = nullptr;
        void              (*fn_parcel_setDataPosition)(android::Parcel *, uint32_t)         = nullptr;
        // IPCThreadState
        void              *(*fn_ipc_self)()                                                        = nullptr;
        uid_t             (*fn_ipc_getCallingUid)(void *)                                          = nullptr;
        pid_t             (*fn_ipc_getCallingPid)(void *)                                          = nullptr;
    };

    static Instance &get() {
        static Instance inst;
        return inst;
    }

    // Helper: resolve a symbol and log the result
    template <typename FnPtr>
    static bool resolve(void *handle, FnPtr &out, const char *symbol) {
        out = reinterpret_cast<FnPtr>(dlsym(handle, symbol));
        if (!out) {
            LOGW("BinderAbi: symbol not found: %s — %s", symbol, dlerror());
            return false;
        }
        return true;
    }
};
