package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Logger
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

class KeyboxVerifierPersistTest {
    private var originalLoggerImpl: Any? = null
    private val warningMessages = mutableListOf<String>()
    private var originalCacheRoot: File? = null

    @Before
    fun setUp() {
        val loggerField = Logger::class.java.getDeclaredField("impl")
        loggerField.isAccessible = true
        originalLoggerImpl = loggerField.get(Logger)

        val newLogger = object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
            override fun i(tag: String, msg: String) {}
            override fun w(tag: String, msg: String) {
                warningMessages.add(msg)
            }
        }
        loggerField.set(Logger, newLogger)

        val cacheRootField = KeyboxVerifier::class.java.getDeclaredField("cacheRoot")
        cacheRootField.isAccessible = true
        originalCacheRoot = cacheRootField.get(KeyboxVerifier) as? File
    }

    @After
    fun tearDown() {
        originalLoggerImpl?.let {
            val loggerField = Logger::class.java.getDeclaredField("impl")
            loggerField.isAccessible = true
            loggerField.set(Logger, it)
        }
        warningMessages.clear()

        originalCacheRoot?.let {
            val cacheRootField = KeyboxVerifier::class.java.getDeclaredField("cacheRoot")
            cacheRootField.isAccessible = true
            cacheRootField.set(KeyboxVerifier, it)
        }
    }

    @Test
    fun testPersistCrlLockedSuccess() {
        val root = Files.createTempDirectory("ct-crl-cache").toFile()
        val cacheRootField = KeyboxVerifier::class.java.getDeclaredField("cacheRoot")
        cacheRootField.isAccessible = true
        cacheRootField.set(KeyboxVerifier, root)

        val fileNameField = KeyboxVerifier::class.java.getDeclaredField("PERSISTED_CRL_FILE")
        fileNameField.isAccessible = true
        val fileName = fileNameField.get(KeyboxVerifier) as String

        val crlJson = """{"entries":{"12345":"REVOKED"}}"""
        val method = KeyboxVerifier::class.java.declaredMethods.first { it.name == "persistCrlLocked" }
        method.isAccessible = true

        val arg = if (method.parameterTypes[0] == String::class.java) crlJson else crlJson.toByteArray()
        method.invoke(KeyboxVerifier, arg)

        val cacheFile = File(root, fileName)
        assertTrue("Cache file should exist", cacheFile.exists())

        val content = String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8)
        assertEquals(crlJson, content)

        root.deleteRecursively()
    }

    @Test
    fun testPersistCrlLockedFailure() {
        val root = Files.createTempDirectory("ct-crl-cache").toFile()
        val cacheRootField = KeyboxVerifier::class.java.getDeclaredField("cacheRoot")
        cacheRootField.isAccessible = true
        cacheRootField.set(KeyboxVerifier, root)

        val fileNameField = KeyboxVerifier::class.java.getDeclaredField("PERSISTED_CRL_FILE")
        fileNameField.isAccessible = true
        val fileName = fileNameField.get(KeyboxVerifier) as String

        // Make it a directory so writeBytes/writeText throws an exception
        val cacheFile = File(root, fileName)
        cacheFile.mkdir()

        val crlJson = """{"entries":{"12345":"REVOKED"}}"""
        val method = KeyboxVerifier::class.java.declaredMethods.first { it.name == "persistCrlLocked" }
        method.isAccessible = true

        val arg = if (method.parameterTypes[0] == String::class.java) crlJson else crlJson.toByteArray()
        method.invoke(KeyboxVerifier, arg)

        // The method should swallow the exception and log a warning
        assertTrue(
            "Expected warning message not logged",
            warningMessages.contains("Could not persist attestation revocation cache")
        )

        root.deleteRecursively()
    }
}
