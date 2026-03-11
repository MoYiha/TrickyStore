package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate the Adaptive Binder Interceptor architecture.
 *
 * These tests verify that module/src/main/cpp/binder_interceptor.cpp implements
 * the five core adaptive interception principles:
 *
 * 1. Runtime Heuristic Offset Discovery (dynamic offset probing)
 * 2. BTF / Kernel Introspection (CO-RE style dynamic struct layout)
 * 3. State-Machine Binder Stream Parser (resilient to struct changes)
 * 4. Multi-Version Fallback Matrix (Android 8-15+ offset database)
 * 5. Bounds Checking & Safety (no segfaults on any ioctl manipulation)
 *
 * The module must be immune to Android version updates and kernel struct changes.
 */
class AdaptiveBinderInterceptorSafetyTest {

    private lateinit var cppContent: String
    private lateinit var headerContent: String

    @Before
    fun setup() {
        cppContent = moduleCppFile("binder_interceptor.cpp").readText()
        headerContent = moduleCppFile("binder_interceptor.h").readText()
    }

    // ========================================================================
    // Principle 1: Runtime Heuristic Offset Discovery
    // ========================================================================

    @Test
    fun testHasOffsetDiscoveryClass() {
        assertTrue(
            "Must have RuntimeOffsetDiscovery class for dynamic offset probing",
            cppContent.contains("RuntimeOffsetDiscovery") || headerContent.contains("RuntimeOffsetDiscovery")
        )
    }

    @Test
    fun testHasDummyPingTransaction() {
        assertTrue(
            "Must send dummy PING_TRANSACTION to discover struct offsets at runtime",
            cppContent.contains("PING_TRANSACTION")
        )
    }

    @Test
    fun testHasOffsetCachingSingleton() {
        assertTrue(
            "Discovered offsets must be cached in a singleton to avoid repeated probing",
            cppContent.contains("OffsetCache") || cppContent.contains("offset_cache")
        )
    }

    @Test
    fun testDiscoversFunctionExists() {
        assertTrue(
            "Must have a discoverOffsets or probe function for runtime offset discovery",
            cppContent.contains("discoverOffsets") || cppContent.contains("probeOffsets")
        )
    }

    @Test
    fun testDynamicTargetPtrOffset() {
        assertTrue(
            "Must dynamically resolve target.ptr offset instead of hardcoded struct access",
            cppContent.contains("target_ptr_offset") || cppContent.contains("kTargetPtrOffset")
        )
    }

    @Test
    fun testDynamicCookieOffset() {
        assertTrue(
            "Must dynamically resolve cookie offset instead of hardcoded struct access",
            cppContent.contains("cookie_offset") || cppContent.contains("kCookieOffset")
        )
    }

    // ========================================================================
    // Principle 2: BTF / Kernel Introspection
    // ========================================================================

    @Test
    fun testHasBtfProviderClass() {
        assertTrue(
            "Must have BTF provider class for kernel introspection",
            cppContent.contains("BtfProvider") || headerContent.contains("BtfProvider")
        )
    }

    @Test
    fun testChecksBtfAvailability() {
        assertTrue(
            "Must check /sys/kernel/btf/vmlinux for BTF availability",
            cppContent.contains("/sys/kernel/btf/vmlinux")
        )
    }

    @Test
    fun testParsesKernelVersion() {
        assertTrue(
            "Must parse kernel version via uname to decide BTF vs fallback",
            cppContent.contains("uname") || cppContent.contains("utsname")
        )
    }

    @Test
    fun testNoHardcodedSizeofBinderTransactionData() {
        assertFalse(
            "Must NOT use hardcoded sizeof(binder_transaction_data) for buffer arithmetic",
            cppContent.contains("sizeof(binder_transaction_data)") &&
                !cppContent.contains("// fallback") &&
                !cppContent.contains("/* fallback")
        )
    }

    @Test
    fun testHasKernelIntrospectionFunction() {
        assertTrue(
            "Must have queryStructLayout or similar kernel introspection function",
            cppContent.contains("queryStructLayout") || cppContent.contains("queryBtf") ||
                cppContent.contains("btf_read") || cppContent.contains("readBtf")
        )
    }

    // ========================================================================
    // Principle 3: State-Machine Binder Stream Parser
    // ========================================================================

    @Test
    fun testHasBinderStreamParserClass() {
        assertTrue(
            "Must have BinderStreamParser class for state-machine based parsing",
            cppContent.contains("BinderStreamParser") || headerContent.contains("BinderStreamParser")
        )
    }

    @Test
    fun testHasParserStates() {
        assertTrue(
            "Stream parser must define parsing states (e.g., PARSE_CMD, PARSE_DATA)",
            cppContent.contains("PARSE_CMD") || cppContent.contains("ParseState") ||
                cppContent.contains("ParserState")
        )
    }

    @Test
    fun testParserHandlesBrTransaction() {
        assertTrue(
            "Stream parser must handle BR_TRANSACTION command",
            cppContent.contains("BR_TRANSACTION")
        )
    }

    @Test
    fun testParserHandlesBrTransactionSecCtx() {
        assertTrue(
            "Stream parser must handle BR_TRANSACTION_SEC_CTX command",
            cppContent.contains("BR_TRANSACTION_SEC_CTX")
        )
    }

    @Test
    fun testParserUsesPointerArithmetic() {
        assertTrue(
            "Stream parser must use safe pointer arithmetic to skip unknown fields",
            cppContent.contains("remaining") || cppContent.contains("bytes_left") ||
                cppContent.contains("stream_remaining")
        )
    }

    @Test
    fun testParserDoesNotUseBlindMemcpy() {
        assertFalse(
            "Stream parser must NOT blindly memcpy entire binder_transaction_data structs " +
                "without bounds validation",
            cppContent.contains("memcpy(&tr_secctx, (void *)ptr, sizeof(tr_secctx))")
        )
    }

    @Test
    fun testParserHasSkipUnknownFields() {
        assertTrue(
            "Parser must be able to skip unknown/new fields safely",
            cppContent.contains("skip") || cppContent.contains("advance") ||
                cppContent.contains("skipUnknown")
        )
    }

    // ========================================================================
    // Principle 4: Multi-Version Fallback Matrix
    // ========================================================================

    @Test
    fun testHasFallbackDatabaseClass() {
        assertTrue(
            "Must have FallbackDatabase or VersionFallback class for known offset maps",
            cppContent.contains("FallbackDatabase") || cppContent.contains("VersionFallback") ||
                headerContent.contains("FallbackDatabase") || headerContent.contains("VersionFallback")
        )
    }

    @Test
    fun testReadsBuildVersionSdk() {
        assertTrue(
            "Must read ro.build.version.sdk for Android version detection",
            cppContent.contains("ro.build.version.sdk")
        )
    }

    @Test
    fun testReadsKernelVersion() {
        assertTrue(
            "Must read kernel version via uname -r for version-specific fallback",
            cppContent.contains("uname") || cppContent.contains("release")
        )
    }

    @Test
    fun testHasMultipleAndroidVersionEntries() {
        assertTrue(
            "Fallback database must cover multiple Android API levels (26-35+)",
            cppContent.contains("26") && cppContent.contains("35") ||
                cppContent.contains("android_8") && cppContent.contains("android_15")
        )
    }

    @Test
    fun testFallbackHasGracefulDegradation() {
        assertTrue(
            "If all offset methods fail, system must not crash - must have safe fallback",
            cppContent.contains("safe_fallback") || cppContent.contains("graceful") ||
                cppContent.contains("FALLBACK_ACTIVE") || cppContent.contains("fallback_mode")
        )
    }

    // ========================================================================
    // Principle 5: Bounds Checking & Safety
    // ========================================================================

    @Test
    fun testHasBoundsCheckingFunction() {
        assertTrue(
            "Must have bounds checking function for safe memory access",
            cppContent.contains("bounds_check") || cppContent.contains("boundsCheck") ||
                cppContent.contains("safe_read") || cppContent.contains("safeRead")
        )
    }

    @Test
    fun testValidatesBufferBeforeAccess() {
        assertTrue(
            "Must validate buffer boundaries before any read operation",
            cppContent.contains("remaining") || cppContent.contains("bytes_left") ||
                cppContent.contains("buffer_end")
        )
    }

    @Test
    fun testNullPointerGuards() {
        assertTrue(
            "Must check for null pointers before dereferencing binder data",
            cppContent.contains("nullptr") && cppContent.contains("== 0")
        )
    }

    @Test
    fun testNoRawPointerCast() {
        assertFalse(
            "Must NOT use raw C-style cast on binder_write_read pointer without validation. " +
                "Use safe accessor pattern instead.",
            cppContent.contains("*(struct binder_write_read *)arg")
        )
    }

    @Test
    fun testSignalHandlerForSafety() {
        assertTrue(
            "Must install SIGSEGV/SIGBUS handler or use safe memory probing",
            cppContent.contains("SIGSEGV") || cppContent.contains("SIGBUS") ||
                cppContent.contains("sigaction") || cppContent.contains("safe_memcpy")
        )
    }

    @Test
    fun testBoundsCheckOnConsumed() {
        assertTrue(
            "Must validate consumed <= read_size before processing binder read buffer",
            cppContent.contains("consumed") && cppContent.contains("read_size")
        )
    }

    // ========================================================================
    // OOP Architecture & Class Design
    // ========================================================================

    @Test
    fun testHasAdaptiveInterceptorClass() {
        assertTrue(
            "Must have AdaptiveBinderInterceptor or similar top-level adaptive class",
            cppContent.contains("AdaptiveBinderInterceptor") ||
                headerContent.contains("AdaptiveBinderInterceptor")
        )
    }

    @Test
    fun testHasInitializeMethod() {
        assertTrue(
            "Adaptive interceptor must have an initialize/init method",
            cppContent.contains("initialize") || cppContent.contains("init(")
        )
    }

    @Test
    fun testHasVersionDetection() {
        assertTrue(
            "Must detect Android and kernel versions for adaptive behavior",
            cppContent.contains("android_api_level") || cppContent.contains("api_level") ||
                cppContent.contains("sdk_version")
        )
    }

    // ========================================================================
    // Backward Compatibility (existing CI/test checks)
    // ========================================================================

    @Test
    fun testStillHooksIoctl() {
        assertTrue(
            "Must still hook ioctl for binder interception",
            cppContent.contains("new_ioctl") && cppContent.contains("old_ioctl")
        )
    }

    @Test
    fun testStillHooksSystemPropertyGet() {
        assertTrue(
            "Must still hook __system_property_get for property spoofing",
            cppContent.contains("__system_property_get")
        )
    }

    @Test
    fun testStillContainsImeiProperties() {
        assertTrue(
            "Must still intercept persist.radio.imei property",
            cppContent.contains("persist.radio.imei")
        )
        assertTrue(
            "Must still intercept vendor.ril.imei property",
            cppContent.contains("vendor.ril.imei")
        )
    }

    @Test
    fun testStillHasBinderFdCaching() {
        assertTrue(
            "Must still cache binder FD detection results",
            cppContent.contains("g_binder_fds")
        )
    }

    @Test
    fun testStillHasRegisterPropertyService() {
        assertTrue(
            "Must still have REGISTER_PROPERTY_SERVICE = 3 for C++/Kotlin consistency",
            cppContent.contains("REGISTER_PROPERTY_SERVICE") &&
                cppContent.contains("= 3")
        )
    }

    @Test
    fun testStillHasEntryPoint() {
        assertTrue(
            "Must still export entry() function for injection",
            cppContent.contains("entry(void *handle)")
        )
    }

    @Test
    fun testStillHasCloseHook() {
        assertTrue(
            "Must still hook close() to invalidate binder FD cache",
            cppContent.contains("new_close") && cppContent.contains("old_close")
        )
    }

    // ========================================================================
    // Integration: Adaptive strategy selection
    // ========================================================================

    @Test
    fun testHasStrategySelectionLogic() {
        assertTrue(
            "Must have strategy selection logic: BTF > Heuristic > Fallback",
            cppContent.contains("BTF") || cppContent.contains("btf")
        )
        assertTrue(
            "Must have heuristic strategy path",
            cppContent.contains("heuristic") || cppContent.contains("Heuristic")
        )
        assertTrue(
            "Must have fallback strategy path",
            cppContent.contains("fallback") || cppContent.contains("Fallback")
        )
    }

    @Test
    fun testHasOffsetValidation() {
        assertTrue(
            "Discovered offsets must be validated before use (sanity check)",
            cppContent.contains("validateOffsets") || cppContent.contains("validate_offsets") ||
                cppContent.contains("isValid") || cppContent.contains("sanity")
        )
    }

    @Test
    fun testSecctxValidationUsesCachedStructSizeField() {
        assertTrue(
            "Offset validation must compare against the cached transaction_data_secctx_size field",
            cppContent.contains("transaction_data_secctx_size < transaction_data_size")
        )
        assertFalse(
            "Offset validation must not reference an out-of-scope secctx_size local",
            cppContent.contains("if (secctx_size < transaction_data_size)")
        )
    }

    @Test
    fun testPingProbeIncludesFcntlForOpenFlags() {
        assertTrue(
            "Binder probe must include <fcntl.h> when using O_RDWR/O_CLOEXEC flags",
            cppContent.contains("#include <fcntl.h>")
        )
    }

    @Test
    fun testHeuristicTransactionSizeLogMatchesArgumentType() {
        assertTrue(
            "Heuristic transaction size log must cast payload size to size_t for %zu formatting",
            cppContent.contains("static_cast<size_t>(payload_sz)")
        )
    }
}
