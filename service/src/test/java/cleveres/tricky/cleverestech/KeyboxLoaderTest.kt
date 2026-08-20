package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class KeyboxLoaderTest {
    @Test
    fun `test override still transfers and wipes XML ownership`() {
        val xml = "<AndroidAttestation/>".toByteArray()
        var observedFilename: String? = null
        var observedFirstByte = 0
        KeyboxLoader.parserOverride = { bytes, filename ->
            observedFilename = filename
            observedFirstByte = bytes.first().toInt()
            emptyList()
        }

        try {
            assertTrue(KeyboxLoader.parse(xml, "fixture.xml").isEmpty())
            assertEquals("fixture.xml", observedFilename)
            assertEquals('<'.code, observedFirstByte)
            assertTrue(xml.all { it == 0.toByte() })
        } finally {
            KeyboxLoader.resetForTesting()
        }
    }

    @Test
    fun `file override receives only broker scope and basename`() {
        var observedScope: KeyboxLoader.FileScope? = null
        var observedFilename: String? = null
        KeyboxLoader.fileParserOverride = { scope, filename ->
            observedScope = scope
            observedFilename = filename
            KeyboxLoader.ParsedFile(
                snapshotSha256 = null,
                keyboxes = emptyList(),
            )
        }

        try {
            assertTrue(
                KeyboxLoader.parseFile(KeyboxLoader.FileScope.KEYBOX_DIRECTORY, "fixture.xml").isEmpty(),
            )
            assertEquals(KeyboxLoader.FileScope.KEYBOX_DIRECTORY, observedScope)
            assertEquals("fixture.xml", observedFilename)
        } finally {
            KeyboxLoader.resetForTesting()
        }
    }

    @Test
    fun `backend outage propagates while transferred XML is still wiped`() {
        val xml = "<AndroidAttestation/>".toByteArray()
        KeyboxLoader.parserOverride = { _, _ -> throw RustBackendUnavailableException() }

        try {
            try {
                KeyboxLoader.parse(xml, "fixture.xml")
                fail("backend outage must not be converted to an invalid keybox result")
            } catch (_: RustBackendUnavailableException) {
                assertTrue(xml.all { it == 0.toByte() })
            }
        } finally {
            KeyboxLoader.resetForTesting()
        }
    }

    @Test
    fun `file backend outage is not converted to an empty parse result`() {
        KeyboxLoader.fileParserOverride = { _, _ -> throw RustBackendUnavailableException() }

        try {
            try {
                KeyboxLoader.parseFile(KeyboxLoader.FileScope.KEYBOX_DIRECTORY, "fixture.xml")
                fail("backend outage must remain distinguishable from rejected input")
            } catch (_: RustBackendUnavailableException) {
                // Expected: backend transport failure must remain distinguishable from invalid input.
            }
        } finally {
            KeyboxLoader.resetForTesting()
        }
    }
}
