package cleveres.tricky.cleverestech

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeWiringContractTest {
    @Test
    fun `module supervisor launches authenticated rust runtime`() {
        val root = locateRoot()
        val service = File(root, "module/template/service.sh").readText()
        val daemon = File(root, "module/template/daemon").readText()
        val dollar = '$'

        assertTrue(service.contains("generate_backend_auth()"))
        assertTrue(service.contains("export CLEVERES_TRICKY_BACKEND_AUTH"))
        assertTrue(service.contains("\"${dollar}MODDIR/daemon\""))
        assertTrue(service.contains("unset CLEVERES_TRICKY_BACKEND_AUTH"))
        assertTrue(daemon.contains("exec \"${dollar}MODDIR/cleverestrickyd\" \"${dollar}MODDIR\""))
    }

    @Test
    fun `daemon supervises adapter and backend generations`() {
        val root = locateRoot()
        val daemon = File(root, "rust/daemon/src/main.rs").readText()

        assertTrue(daemon.contains("spawn_android_adapter(&module_dir)"))
        assertTrue(daemon.contains("adapter_identity.publish(adapter.id())"))
        assertTrue(daemon.contains("supervise_backend(backend_dir"))
        assertTrue(daemon.contains("ADAPTER_MAX_BACKOFF"))
        assertTrue(daemon.contains("BACKEND_MAX_BACKOFF"))
        assertTrue(daemon.contains("AdapterChanged"))
    }

    @Test
    fun `android broker client authenticates direct daemon parent without procfs dependency`() {
        val root = locateRoot()
        val secureFile =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/util/RustSecureFileOperations.kt",
            ).readText()

        assertTrue(secureFile.contains("Os.getppid()"))
        assertTrue(secureFile.contains("peer.uid != 0"))
        assertTrue(secureFile.contains("peer.gid != 0"))
        assertTrue(secureFile.contains("peer.pid != parentPid"))
        assertTrue(secureFile.contains("awaitAdapterRegistration()"))
        assertTrue(secureFile.contains("STARTUP_RETRY_ATTEMPTS"))
        assertFalse(secureFile.contains("/proc/"))
    }

    @Test
    fun `backend requires capability and unprivileged peer identity`() {
        val root = locateRoot()
        val backendClient =
            File(
                root,
                "service/src/main/java/cleveres/tricky/cleverestech/NativeBackend.kt",
            ).readText()
        val backendServer = File(root, "rust/backend/src/main.rs").readText()
        val backendInstance = File(root, "rust/backend/src/backend_instance.rs").readText()

        assertTrue(backendClient.contains("BackendAuth.fromEnvironment()"))
        assertTrue(backendClient.contains("peer.uid != ANDROID_AID_NOBODY"))
        assertTrue(backendClient.contains("peer.gid != ANDROID_GID_NOBODY"))
        assertTrue(backendServer.contains("setgid(ANDROID_GID_NOBODY)"))
        assertTrue(backendServer.contains("setuid(ANDROID_AID_NOBODY)"))
        assertTrue(backendInstance.contains("BACKEND_AUTH_ENV"))
        assertTrue(backendInstance.contains("backend handshake request rejected"))
    }

    @Test
    fun `web ui registration precedes backend readiness`() {
        val root = locateRoot()
        val source = File(root, "service/src/main/java/cleveres/tricky/cleverestech/Main.kt").readText()
        val entry = source.indexOf("fun main(args: Array<String>)")
        val registration = source.indexOf("startWebUiBridge(configDir, isTampered)", entry)
        val backendWait = source.indexOf("NativeBackend.awaitReady(BACKEND_STARTUP_TIMEOUT_MS)", entry)

        assertTrue(entry >= 0)
        assertTrue(registration > entry)
        assertTrue(backendWait > registration)
    }

    private fun locateRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(6) {
            if (File(current, "service").isDirectory && File(current, "rust").isDirectory) return current
            current = current.parentFile ?: return@repeat
        }
        error("Repository root not found")
    }
}
