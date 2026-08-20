package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class StoredKeyboxInventoryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `direct root XML and managed XML CBOX are inventoried with explicit scope`() {
        val root = temp.newFolder("config")
        File(root, "A1B2C3.xml").writeText("root")
        val managed = File(root, "keyboxes").also { assertTrue(it.mkdirs()) }
        File(managed, "D4E5F6.xml").writeText("managed")
        File(managed, "LOCKED.cbox").writeText("encrypted")
        File(root, "ignored.txt").writeText("ignored")
        val items = StoredKeyboxInventory.list(root)
        assertEquals(listOf("keyboxes:D4E5F6.xml", "keyboxes:LOCKED.cbox", "root:A1B2C3.xml"), items.map { it.id }.sorted())
        assertEquals(2, StoredKeyboxInventory.runtimeXmlSources(root).size)
    }

    @Test
    fun `duplicate basename across scopes remains runtime compatible`() {
        val root = temp.newFolder("duplicate")
        File(root, "same.xml").writeText("root")
        val managed = File(root, "keyboxes").also { assertTrue(it.mkdirs()) }
        File(managed, "same.xml").writeText("managed")

        assertEquals(
            listOf("keyboxes:same.xml", "root:same.xml"),
            StoredKeyboxInventory.runtimeXmlSources(root).map { it.id }.sorted(),
        )
    }

    @Test
    fun `oversized XML is excluded before content stamping`() {
        val root = temp.newFolder("oversized")
        val oversized = File(root, "oversized.xml")
        RandomAccessFile(oversized, "rw").use { file ->
            file.setLength(StoredKeyboxInventory.MAX_XML_BYTES + 1)
        }
        var stampAttempts = 0

        val sources =
            StoredKeyboxInventory.runtimeXmlSources(root) {
                stampAttempts++
                1L
            }

        assertTrue(sources.isEmpty())
        assertEquals(0, stampAttempts)
    }

    @Test
    fun `content stamp failure excludes source from cache admission`() {
        val root = temp.newFolder("unstable")
        File(root, "unstable.xml").writeText("keybox")

        val sources = StoredKeyboxInventory.runtimeXmlSources(root) { null }

        assertTrue(sources.isEmpty())
    }

    @Test
    fun `runtime XML source count is bounded`() {
        val root = temp.newFolder("bounded")
        repeat(StoredKeyboxInventory.MAX_ACTIVE_XML_SOURCES + 1) { index -> File(root, "cert-$index.xml").writeText("x") }
        assertThrows(IllegalArgumentException::class.java) { StoredKeyboxInventory.runtimeXmlSources(root) }
    }
}
