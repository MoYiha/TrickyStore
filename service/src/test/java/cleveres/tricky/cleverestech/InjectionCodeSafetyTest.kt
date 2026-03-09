package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate injection code correctness at source level.
 *
 * These tests scan module/src/main/cpp/inject/main.cpp for patterns
 * that are critical to correct SCM_RIGHTS FD passing, preventing
 * regressions like the "Invalid cmsg received from remote process" bug.
 */
class InjectionCodeSafetyTest {

    private lateinit var injectMainContent: String
    private lateinit var injectUtilsContent: String

    @Before
    fun setup() {
        injectMainContent = moduleCppFile("inject/main.cpp").readText()
        injectUtilsContent = moduleCppFile("inject/utils.cpp").readText()
    }

    // ===============================
    // SCM_RIGHTS FD passing integrity
    // ===============================

    @Test
    fun testRemoteMsgHdrHasIov() {
        assertTrue(
            "Remote msghdr must have msg_iov set for recvmsg to properly receive ancillary data (SCM_RIGHTS)",
            injectMainContent.contains("msg_hdr.msg_iov")
        )
    }

    @Test
    fun testRemoteMsgHdrHasIovLen() {
        assertTrue(
            "Remote msghdr must have msg_iovlen set to at least 1 for SCM_RIGHTS",
            injectMainContent.contains("msg_hdr.msg_iovlen = 1")
        )
    }

    @Test
    fun testLocalMsgHdrHasIov() {
        assertTrue(
            "Local msghdr for sendmsg must have msg_iov set for ancillary data delivery",
            injectMainContent.contains("local_msg_hdr.msg_iov")
        )
    }

    @Test
    fun testLocalMsgHdrHasIovLen() {
        assertTrue(
            "Local msghdr for sendmsg must have msg_iovlen set to at least 1",
            injectMainContent.contains("local_msg_hdr.msg_iovlen = 1")
        )
    }

    @Test
    fun testCmsgLenValidationUsesLessThan() {
        assertTrue(
            "cmsg_len validation must use '<' (less-than) instead of '!=' for kernel compatibility across Android versions",
            injectMainContent.contains("cmsg_len < CMSG_LEN")
        )
    }

    @Test
    fun testCmsgLenValidationDoesNotUseStrictEquality() {
        assertFalse(
            "cmsg_len validation must NOT use '!=' (strict inequality) as kernel may pad cmsg_len",
            injectMainContent.contains("cmsg_len != CMSG_LEN")
        )
    }

    @Test
    fun testUsesSCMRights() {
        assertTrue(
            "Injection must use SCM_RIGHTS for FD passing",
            injectMainContent.contains("SCM_RIGHTS")
        )
    }

    @Test
    fun testUsesAbstractNamespace() {
        assertTrue(
            "Socket must use abstract namespace (sun_path + 1) to avoid filesystem races",
            injectMainContent.contains("sun_path + 1")
        )
    }

    @Test
    fun testUsesSockDgramCloexec() {
        assertTrue(
            "Socket creation must use SOCK_CLOEXEC to prevent FD leak to children",
            injectMainContent.contains("SOCK_CLOEXEC")
        )
    }

    // ================================
    // Error handling & cleanup
    // ================================

    @Test
    fun testPtraceDetachOnError() {
        val detachCount = Regex("PTRACE_DETACH").findAll(injectMainContent).count()
        assertTrue(
            "inject_library must call PTRACE_DETACH in all error paths (found $detachCount, need at least 5)",
            detachCount >= 5
        )
    }

    @Test
    fun testRemoteCloseOnError() {
        assertTrue(
            "Must close remote socket FD on error to prevent resource leak in target process",
            injectMainContent.contains("close_remote_fd_func(remote_fd)")
        )
    }

    @Test
    fun testRegistersBackupRestored() {
        assertTrue(
            "Original registers must be backed up for restoration after injection",
            injectMainContent.contains("memcpy(&backup")
        )
        assertTrue(
            "Original registers must be restored before detaching",
            injectMainContent.contains("set_regs(pid, backup)")
        )
    }

    // ================================
    // dlopen/dlsym correctness
    // ================================

    @Test
    fun testUsesAndroidDlopenExt() {
        assertTrue(
            "Must use android_dlopen_ext (not plain dlopen) to pass FD-based library",
            injectMainContent.contains("android_dlopen_ext")
        )
    }

    @Test
    fun testUsesLibraryFdFlag() {
        assertTrue(
            "Must set ANDROID_DLEXT_USE_LIBRARY_FD for FD-based dlopen",
            injectMainContent.contains("ANDROID_DLEXT_USE_LIBRARY_FD")
        )
    }

    @Test
    fun testCallsDlsymForEntry() {
        assertTrue(
            "Must use dlsym to resolve entry function in loaded library",
            injectMainContent.contains("dlsym")
        )
    }

    // ================================
    // ptrace utility safety
    // ================================

    @Test
    fun testUtilsHasProcVmReadWrite() {
        assertTrue(
            "Utils should use process_vm_readv/writev for efficient memory access",
            injectUtilsContent.contains("process_vm_readv") || injectUtilsContent.contains("process_vm_writev")
        )
    }

    @Test
    fun testUtilsHasStackAlignment() {
        assertTrue(
            "Utils must implement stack alignment to prevent crashes on ARM/x86",
            injectUtilsContent.contains("align_stack")
        )
    }
}
