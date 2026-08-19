package cleveres.tricky.encryptor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxZipReaderTest {
    @Test
    fun `streams a single XML keybox directly and wipes its buffer`() {
        val xml = "<AndroidAttestation><Keybox/></AndroidAttestation>".toByteArray()
        val observed = mutableListOf<Pair<String, ByteArray>>()

        val count =
            KeyboxImportReader.process(
                ByteArrayInputStream(xml),
                "single-keybox.xml",
                validateXml = { it.isNotEmpty() && it[0] == '<'.code.toByte() },
                onKeybox = { name, bytes -> observed += name to bytes },
            )

        assertEquals(1, count)
        assertEquals("single-keybox.xml", observed.single().first)
        assertTrue(observed.single().second.all { it == 0.toByte() })
    }

    @Test
    fun `single XML import strips path-like display names`() {
        val observed = mutableListOf<String>()
        KeyboxImportReader.process(
            ByteArrayInputStream("<keybox/>".toByteArray()),
            "../nested/keybox.xml",
            validateXml = { true },
            onKeybox = { name, _ -> observed += name },
        )
        assertEquals(listOf("keybox.xml"), observed)
    }

    @Test
    fun `zeroizes rejected single XML buffer`() {
        val observed = mutableListOf<ByteArray>()

        assertThrows(IOException::class.java) {
            KeyboxImportReader.process(
                ByteArrayInputStream("not xml".toByteArray()),
                "bad.xml",
                validateXml = { bytes -> observed += bytes; false },
                onKeybox = { _, _ -> error("must not emit invalid XML") },
            )
        }

        assertEquals(1, observed.size)
        assertTrue(observed.single().all { it == 0.toByte() })
    }

    @Test
    fun `rejects oversized single XML before validation`() {
        val oversized = ByteArray(KeyboxZipReader.MAX_XML_BYTES + 1) { 'x'.code.toByte() }
        var validationCalls = 0

        assertThrows(IOException::class.java) {
            KeyboxImportReader.process(
                ByteArrayInputStream(oversized),
                "large.xml",
                validateXml = { validationCalls++; true },
                onKeybox = { _, _ -> },
            )
        }

        assertEquals(0, validationCalls)
    }

    @Test
    fun `detects ZIP batches by signature and streams entries in order`() {
        val archive = zipOf("first.xml" to "<first/>".toByteArray(), "second.xml" to "<second/>".toByteArray())
        val names = mutableListOf<String>()
        val observed = mutableListOf<ByteArray>()

        val count =
            KeyboxImportReader.process(
                ByteArrayInputStream(archive),
                "anything.bin",
                validateXml = { true },
                onKeybox = { name, bytes -> names += name; observed += bytes },
            )

        assertEquals(2, count)
        assertEquals(listOf("first.xml", "second.xml"), names)
        assertTrue(observed.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    @Test
    fun `supports batches larger than the old 64 file ceiling without retaining XML`() {
        val entries = (0 until 80).map { "keybox-$it.xml" to "<k/>".toByteArray() }
        var emitted = 0

        val count =
            KeyboxZipReader.process(
                ByteArrayInputStream(zipOf(*entries.toTypedArray())),
                validateXml = { true },
                onKeybox = { _, _ -> emitted++ },
            )

        assertEquals(80, count)
        assertEquals(80, emitted)
        assertEquals(10_000, KeyboxZipReader.MAX_KEYBOX_FILES)
    }

    @Test
    fun `rejects the 10001st XML entry`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(KeyboxZipReader.MAX_KEYBOX_FILES + 1) { index ->
                zip.putNextEntry(ZipEntry("k$index.xml"))
                zip.write("<k/>".toByteArray())
                zip.closeEntry()
            }
        }

        assertThrows(IOException::class.java) {
            KeyboxZipReader.process(
                ByteArrayInputStream(output.toByteArray()),
                validateXml = { true },
                onKeybox = { _, _ -> },
            )
        }
    }

    @Test
    fun `rejects oversized unrelated expansion before later keyboxes`() {
        val archive = zipOf("padding.bin" to ByteArray(1024 * 1024 + 1), "keybox.xml" to "<k/>".toByteArray())

        assertThrows(IOException::class.java) {
            KeyboxZipReader.process(
                ByteArrayInputStream(archive),
                validateXml = { true },
                onKeybox = { _, _ -> },
            )
        }
    }

    @Test
    fun `rejects archives without XML keyboxes`() {
        val archive = zipOf("readme.txt" to "hello".toByteArray())

        assertThrows(IOException::class.java) {
            KeyboxZipReader.process(
                ByteArrayInputStream(archive),
                validateXml = { true },
                onKeybox = { _, _ -> },
            )
        }
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
