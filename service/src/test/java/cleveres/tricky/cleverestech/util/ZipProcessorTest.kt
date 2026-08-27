package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Logger
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipProcessorTest {

    private var originalLoggerImpl: Logger.LogImpl? = null

    @Before
    fun setUp() {
        // Save the original logger using reflection
        val implField = Logger::class.java.getDeclaredField("impl")
        implField.isAccessible = true
        originalLoggerImpl = implField.get(Logger) as Logger.LogImpl

        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
            override fun i(tag: String, msg: String) {}
            override fun w(tag: String, msg: String) {}
        })
    }

    @After
    fun tearDown() {
        originalLoggerImpl?.let {
            Logger.setImpl(it)
        }
    }

    private fun createZipStream(entries: Map<String, ByteArray>): InputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, content) ->
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(content)
                zos.closeEntry()
            }
        }
        return ByteArrayInputStream(baos.toByteArray())
    }

    @Test
    fun testValidZipWithConfigPassword() {
        val cboxContent = "cbox-content".toByteArray()
        val configContent = """{"password": "test-password"}""".toByteArray()
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to cboxContent,
                "config.json" to configContent
            )
        )

        val result = ZipProcessor.process(stream)

        assertNotNull(result)
        assertEquals("test-password", result?.password)
        assertEquals(1, result?.cboxFiles?.size)
        assertEquals("test1.cbox", result?.cboxFiles?.get(0)?.first)
        assertArrayEquals(cboxContent, result?.cboxFiles?.get(0)?.second)
    }

    @Test
    fun testValidZipWithPasswordTxt() {
        val cboxContent = "cbox-content".toByteArray()
        val passwordContent = "test-password-txt\n".toByteArray()
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to cboxContent,
                "password.txt" to passwordContent
            )
        )

        val result = ZipProcessor.process(stream)

        assertNotNull(result)
        assertEquals("test-password-txt", result?.password) // Should trim the newline
        assertEquals(1, result?.cboxFiles?.size)
        assertEquals("test1.cbox", result?.cboxFiles?.get(0)?.first)
        assertArrayEquals(cboxContent, result?.cboxFiles?.get(0)?.second)
    }

    @Test
    fun testConfigPasswordOverridesPasswordTxt() {
        val cboxContent = "cbox-content".toByteArray()
        val configContent = """{"password": "config-password"}""".toByteArray()
        val passwordContent = "txt-password".toByteArray()
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to cboxContent,
                "config.json" to configContent,
                "password.txt" to passwordContent
            )
        )

        val result = ZipProcessor.process(stream)

        assertNotNull(result)
        assertEquals("config-password", result?.password)
    }

    @Test
    fun testNoCboxFilesReturnsNull() {
        val configContent = """{"password": "test-password"}""".toByteArray()
        val stream = createZipStream(
            mapOf(
                "config.json" to configContent
            )
        )

        val result = ZipProcessor.process(stream)
        assertNull(result)
    }

    @Test
    fun testEmptyConfigPasswordFallsBackToPasswordTxt() {
        val cboxContent = "cbox-content".toByteArray()
        val configContent = """{"password": ""}""".toByteArray()
        val passwordContent = "txt-password".toByteArray()
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to cboxContent,
                "config.json" to configContent,
                "password.txt" to passwordContent
            )
        )

        val result = ZipProcessor.process(stream)

        assertNotNull(result)
        assertEquals("txt-password", result?.password)
    }

    @Test
    fun testUnsafeEntryNameReturnsNull() {
        val cboxContent = "cbox-content".toByteArray()
        val unsafeNames = listOf("../test1.cbox", "dir/test1.cbox", "dir\\test1.cbox")

        for (unsafeName in unsafeNames) {
            val stream = createZipStream(mapOf(unsafeName to cboxContent))
            val result = ZipProcessor.process(stream)
            assertNull("Failed on $unsafeName", result)
        }
    }

    @Test
    fun testTooManyEntriesReturnsNull() {
        val entries = mutableMapOf<String, ByteArray>()
        for (i in 1..129) {
            entries["test${i}.cbox"] = "content".toByteArray()
        }
        val stream = createZipStream(entries)

        val result = ZipProcessor.process(stream)
        assertNull(result)
    }

    @Test
    fun testTooManyCboxFilesReturnsNull() {
        val entries = mutableMapOf<String, ByteArray>()
        for (i in 1..65) {
            entries["test${i}.cbox"] = "content".toByteArray()
        }
        val stream = createZipStream(entries)

        val result = ZipProcessor.process(stream)
        assertNull(result)
    }

    @Test
    fun testMaxPasswordCharsExceededReturnsNull() {
        val cboxContent = "cbox-content".toByteArray()
        val longPassword = "a".repeat(1025)
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to cboxContent,
                "password.txt" to longPassword.toByteArray()
            )
        )

        val result = ZipProcessor.process(stream)
        assertNull(result)
    }

    @Test
    fun testEntrySizeExceededReturnsNull() {
        val cboxContent = ByteArray(5 * 1024 * 1024 + 1) // 5MB + 1 byte
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to cboxContent
            )
        )

        val result = ZipProcessor.process(stream)
        assertNull(result)
    }

    @Test
    fun testMetadataSizeExceededReturnsNull() {
        val cboxContent = "cbox-content".toByteArray()
        val largePassword = ByteArray(64 * 1024 + 1) // 64KB + 1 byte
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to cboxContent,
                "password.txt" to largePassword
            )
        )

        val result = ZipProcessor.process(stream)
        assertNull(result)
    }

    @Test
    fun testTotalSizeExceededReturnsNull() {
        val content1 = ByteArray(4 * 1024 * 1024)
        val content2 = ByteArray(4 * 1024 * 1024)
        val content3 = ByteArray(3 * 1024 * 1024)
        val stream = createZipStream(
            mapOf(
                "test1.cbox" to content1,
                "test2.cbox" to content2,
                "test3.cbox" to content3
            )
        )

        val result = ZipProcessor.process(stream)
        assertNull(result)
    }
}
