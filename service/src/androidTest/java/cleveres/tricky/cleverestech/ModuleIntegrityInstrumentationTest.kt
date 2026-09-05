package cleveres.tricky.cleverestech

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ModuleIntegrityInstrumentationTest {

    private lateinit var testModuleDir: File
    private lateinit var keyPair: java.security.KeyPair
    private lateinit var publicKeyHex: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        testModuleDir = File(context.cacheDir, "test_module_${System.currentTimeMillis()}").apply { mkdirs() }

        val kpg = KeyPairGenerator.getInstance("Ed25519")
        keyPair = kpg.generateKeyPair()
        publicKeyHex = bytesToHex(keyPair.public.encoded.takeLast(32).toByteArray())

        ModuleIntegrityVerifier.resetForTesting()
        ModuleIntegrityVerifier.moduleDirProvider = { testModuleDir.absolutePath }
        ModuleIntegrityVerifier.trustedPublicKeyProvider = { keyPair.public.encoded.takeLast(32).toByteArray() }
        ModuleIntegrityVerifier.allowUnsignedManifest = true
        ModuleIntegrityWatcher.resetForTesting()
    }

    @After
    fun tearDown() {
        ModuleIntegrityWatcher.resetForTesting()
        ModuleIntegrityVerifier.resetForTesting()
        IntegrityViolationHandler.resetForTesting()
        testModuleDir.deleteRecursively()
    }

    @Test
    fun checkFileTypeModeEnforcesExecutableOnAndroid() {
        val execFile = File(testModuleDir, "test_exec.sh").apply {
            writeText("#!/system/bin/sh\necho test\n")
            setExecutable(true, false)
        }
        val checkMethod = ModuleIntegrityVerifier::class.java.getDeclaredMethod(
            "checkFileTypeMode",
            java.nio.file.Path::class.java,
            String::class.java,
        ).apply { isAccessible = true }

        val passesExec = checkMethod.invoke(ModuleIntegrityVerifier, execFile.toPath(), "executable") as Boolean
        assertTrue("Executable with +x must pass mode check on Android", passesExec)

        val regularFile = File(testModuleDir, "test_regular.txt").apply {
            writeText("regular content")
            setExecutable(true, false)
        }
        val passesRegWithExecBit = checkMethod.invoke(ModuleIntegrityVerifier, regularFile.toPath(), "regular") as Boolean
        assertTrue("Regular file with +x must be tolerated on Android (overlayfs)", passesRegWithExecBit)
    }

    @Test
    fun cleanModuleTreePassesIntegrityVerificationOnAndroid() {
        populateMockModule(testModuleDir, sign = true)

        val result = ModuleIntegrityVerifier.verifyFull()
        assertTrue(
            "Clean module must pass verification without violations, got: ${(result as? IntegrityResult.Fail)?.violations}",
            result is IntegrityResult.Pass,
        )
    }

    @Test
    fun tamperedPayloadFailsVerificationOnAndroid() {
        populateMockModule(testModuleDir, sign = true)

        val daemonFile = File(testModuleDir, "daemon")
        daemonFile.writeText("#!/system/bin/sh\necho TAMPERED\n")

        val result = ModuleIntegrityVerifier.verifyFull()
        assertTrue("Tampered payload must fail integrity verification", result is IntegrityResult.Fail)
        val fail = result as IntegrityResult.Fail
        assertTrue(
            "Violations must record hash mismatch for daemon",
            fail.violations.any { it.contains("Hash mismatch for: daemon") },
        )
    }

    @Test
    fun missingCriticalPayloadFailsVerificationOnAndroid() {
        populateMockModule(testModuleDir, sign = true)

        val injectFile = File(testModuleDir, "inject")
        injectFile.delete()

        val result = ModuleIntegrityVerifier.verifyFull()
        assertTrue("Missing critical payload must fail integrity verification", result is IntegrityResult.Fail)
        val fail = result as IntegrityResult.Fail
        assertTrue(
            "Violations must record missing inject binary",
            fail.violations.any { it.contains("Missing critical payload: inject") },
        )
    }

    @Test
    fun unexpectedBinaryInModuleFailsVerificationOnAndroid() {
        populateMockModule(testModuleDir, sign = true)

        File(testModuleDir, "malicious.so").apply {
            writeBytes(ByteArray(64) { 0x42 })
        }

        val result = ModuleIntegrityVerifier.verifyFull()
        assertTrue("Unexpected .so file must fail integrity verification", result is IntegrityResult.Fail)
        val fail = result as IntegrityResult.Fail
        assertTrue(
            "Violations must record unexpected file",
            fail.violations.any { it.contains("malicious.so") },
        )
    }

    @Test
    fun runtimeDirectoriesAndSha256FilesAreIgnoredOnAndroid() {
        populateMockModule(testModuleDir, sign = true)

        File(testModuleDir, "keyboxes").mkdirs()
        File(testModuleDir, "keyboxes/active_keybox.xml").writeText("<xml>test</xml>")
        File(testModuleDir, "logs").mkdirs()
        File(testModuleDir, "logs/native_runtime.log").writeText("startup log")
        File(testModuleDir, "system").mkdirs()
        File(testModuleDir, "system/placeholder").writeText("placeholder")
        File(testModuleDir, "service.apk.sha256").writeText("abcdef1234567890")

        val result = ModuleIntegrityVerifier.verifyFull()
        assertTrue(
            "Runtime directories (keyboxes, logs, system) and .sha256 files must be ignored, got: ${(result as? IntegrityResult.Fail)?.violations}",
            result is IntegrityResult.Pass,
        )
    }

    @Test
    fun unsignedManifestPassesWhenAllowedOnAndroid() {
        ModuleIntegrityVerifier.allowUnsignedManifest = true
        populateMockModule(testModuleDir, sign = false)

        val result = ModuleIntegrityVerifier.verifyFull()
        assertTrue(
            "Unsigned manifest must pass when allowUnsignedManifest is true, got: ${(result as? IntegrityResult.Fail)?.violations}",
            result is IntegrityResult.Pass,
        )
    }

    @Test
    fun unsignedManifestFailsWhenForbiddenOnAndroid() {
        ModuleIntegrityVerifier.allowUnsignedManifest = false
        populateMockModule(testModuleDir, sign = false)

        val result = ModuleIntegrityVerifier.verifyFull()
        assertTrue(
            "Unsigned manifest must fail when allowUnsignedManifest is false",
            result is IntegrityResult.Fail,
        )
    }

    @Test
    fun watcherDetectsFileModificationOnAndroid() {
        populateMockModule(testModuleDir, sign = true)
        val manifest = ModuleIntegrityVerifier.loadManifest()
        assertNotNull("Manifest must load", manifest)

        val violationLatch = CountDownLatch(1)
        var detectedViolations = emptyList<String>()

        ModuleIntegrityWatcher.start(testModuleDir, manifest!!) { violations ->
            detectedViolations = violations
            violationLatch.countDown()
        }

        File(testModuleDir, "daemon").writeText("#!/system/bin/sh\nexit 1\n")

        val triggered = violationLatch.await(3, TimeUnit.SECONDS)
        assertTrue("Watcher must detect file modification on Android within timeout", triggered)
        assertTrue("Violations must not be empty", detectedViolations.isNotEmpty())
    }

    @Test
    fun watcherDetectsManifestDeletionOnAndroid() {
        populateMockModule(testModuleDir, sign = true)
        val manifest = requireNotNull(ModuleIntegrityVerifier.loadManifest())
        val violationLatch = CountDownLatch(1)
        var detectedViolations = emptyList<String>()
        ModuleIntegrityWatcher.start(testModuleDir, manifest) { violations ->
            detectedViolations = violations
            violationLatch.countDown()
        }

        assertTrue(File(testModuleDir, "integrity_manifest.json").delete())

        assertTrue("Real manifest DELETE must reach verification", violationLatch.await(5, TimeUnit.SECONDS))
        assertTrue(detectedViolations.any { it.contains("Manifest") })
    }

    @Test
    fun watcherReverifiesAWriteWhoseDescriptorRemainsOpenOnAndroid() {
        populateMockModule(testModuleDir, sign = true)
        val manifest = requireNotNull(ModuleIntegrityVerifier.loadManifest())
        ModuleIntegrityWatcher.fullVerificationDelayMs = 25L
        ModuleIntegrityWatcher.writeGraceMs = 200L
        val violationLatch = CountDownLatch(1)
        var detectedViolations = emptyList<String>()
        ModuleIntegrityWatcher.start(testModuleDir, manifest) { violations ->
            detectedViolations = violations
            violationLatch.countDown()
        }

        java.io.FileOutputStream(File(testModuleDir, "service.apk"), true).use { output ->
            output.write("changed".toByteArray())
            output.flush()
            assertTrue("Verification must resume before CLOSE_WRITE", violationLatch.await(5, TimeUnit.SECONDS))
            assertTrue(detectedViolations.any { it.contains("Hash mismatch") })
        }
    }

    @Test
    fun watcherAcceptsAnUnchangedManifestAfterAtomicReplacementOnAndroid() {
        populateMockModule(testModuleDir, sign = true)
        val manifestFile = File(testModuleDir, "integrity_manifest.json")
        val replacement = File(testModuleDir, "replacement.sha256").apply { writeBytes(manifestFile.readBytes()) }
        val manifest = requireNotNull(ModuleIntegrityVerifier.loadManifest())
        val verified = CountDownLatch(1)
        val violationLatch = CountDownLatch(1)
        ModuleIntegrityWatcher.fullVerifier = {
            ModuleIntegrityVerifier.verifyFull().also { if (it is IntegrityResult.Pass) verified.countDown() }
        }
        ModuleIntegrityWatcher.start(testModuleDir, manifest) { violationLatch.countDown() }

        java.nio.file.Files.move(
            replacement.toPath(),
            manifestFile.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )

        assertTrue("Atomic manifest replacement must be cryptographically checked", verified.await(5, TimeUnit.SECONDS))
        assertFalse("Unchanged signed bytes must not violate", violationLatch.await(200, TimeUnit.MILLISECONDS))
    }

    private fun populateMockModule(dir: File, sign: Boolean) {
        val files = listOf(
            Triple("daemon", "#!/system/bin/sh\nexec ./cleverestrickyd\n", "executable"),
            Triple("inject", "ELF_MOCK_INJECT", "executable"),
            Triple("webui_bridge", "ELF_MOCK_WEBUI", "executable"),
            Triple("cleverestrickyd", "ELF_MOCK_DAEMON", "executable"),
            Triple("cleverestricky_backend", "ELF_MOCK_BACKEND", "executable"),
            Triple("service.apk", "APK_MOCK_SERVICE", "regular"),
            Triple("service.sh", "#!/system/bin/sh\nexit 0\n", "executable"),
            Triple("post-fs-data.sh", "#!/system/bin/sh\nexit 0\n", "executable"),
            Triple("action.sh", "#!/system/bin/sh\nexit 0\n", "executable"),
            Triple("sepolicy.rule", "allow untrusted_app default_prop file read;\n", "regular"),
            Triple("module.prop", "id=cleverestricky\nname=CleveresTricky\nversion=V2.7.2\n", "regular"),
            Triple("libcleverestricky.so", "SO_MOCK_LIB", "regular"),
            Triple("webroot/index.html", "<html><body>CleveresTricky</body></html>", "regular"),
            Triple("webroot/bridge.js", "function bridge() {}", "regular"),
            Triple("webroot/policy.js", "function policy() {}", "regular"),
            Triple("webroot/ux.js", "function ux() {}", "regular"),
        )

        val manifestEntries = mutableListOf<JSONObject>()
        for ((relPath, content, type) in files) {
            val file = File(dir, relPath).apply {
                parentFile?.mkdirs()
                writeText(content)
                if (type == "executable") setExecutable(true, false)
            }
            manifestEntries.add(
                JSONObject()
                    .put("path", relPath)
                    .put("sha256", sha256Hex(file.readBytes()))
                    .put("type", type),
            )
        }

        val sortedEntries = manifestEntries.sortedBy { it.getString("path") }
        val canonicalData = buildString {
            append("1\n")
            for (entry in sortedEntries) {
                append(entry.getString("path")).append('\n')
                append(entry.getString("sha256").lowercase()).append('\n')
                append(entry.getString("type")).append('\n')
            }
        }

        val signatureHex = if (sign) {
            val signer = Signature.getInstance("Ed25519")
            signer.initSign(keyPair.private)
            signer.update(canonicalData.toByteArray(Charsets.UTF_8))
            bytesToHex(signer.sign())
        } else {
            ""
        }

        val manifestJson = JSONObject()
            .put("version", 1)
            .put("files", JSONArray(sortedEntries))
            .put("signature", signatureHex)

        File(dir, "integrity_manifest.json").writeText(manifestJson.toString(2))
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return bytesToHex(digest)
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
