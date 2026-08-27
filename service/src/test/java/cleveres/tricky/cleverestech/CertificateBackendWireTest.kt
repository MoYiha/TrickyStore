package cleveres.tricky.cleverestech


import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CertificateBackendWireTest {
    @Test
    fun `inspection response decodes strict fields and wipes transport bytes`() {
        val response = ByteArray(83)
        response[0] = 1
        response[1] = 0x07
        writeU16(response, 2, (1 shl 0) or (1 shl 4) or (1 shl 8))
        writeOptionalI32(response, 4, 20260105)
        writeOptionalI32(response, 9, null)
        writeOptionalI32(response, 14, 20260205)
        for (index in 0 until 32) {
            response[19 + index] = (index + 1).toByte()
            response[51 + index] = (0x40 + index).toByte()
        }

        val inspection = CertificateBackend.decodeInspection(response)

        assertEquals(20260105, inspection.systemPatch)
        assertNull(inspection.vendorPatch)
        assertEquals(20260205, inspection.bootPatch)
        assertEquals((1 shl 0) or (1 shl 4) or (1 shl 8), inspection.presentIdMask)
        assertTrue(inspection.supportsModuleHash)
        assertArrayEquals(ByteArray(32) { (it + 1).toByte() }, inspection.originalBootKey)
        assertArrayEquals(ByteArray(32) { (0x40 + it).toByte() }, inspection.originalBootHash)
        assertTrue(response.all { it == 0.toByte() })

        inspection.wipe()
        assertTrue(requireNotNull(inspection.originalBootKey).all { it == 0.toByte() })
        assertTrue(requireNotNull(inspection.originalBootHash).all { it == 0.toByte() })
    }

    @Test
    fun `reserved flags and noncanonical absent fields fail closed`() {
        val reserved = ByteArray(83)
        reserved[0] = 1
        reserved[1] = 0x08
        assertThrows(RustBackendUnavailableException::class.java) {
            CertificateBackend.decodeInspection(reserved)
        }
        assertTrue(reserved.all { it == 0.toByte() })

        val noncanonical = ByteArray(83)
        noncanonical[0] = 1
        writeI32(noncanonical, 5, 1)
        assertThrows(RustBackendUnavailableException::class.java) {
            CertificateBackend.decodeInspection(noncanonical)
        }
        assertTrue(noncanonical.all { it == 0.toByte() })
    }

    @Test
    fun `absent boot digests remain absent`() {
        val response = ByteArray(83)
        response[0] = 1
        val inspection = CertificateBackend.decodeInspection(response)
        assertFalse(inspection.supportsModuleHash)
        assertNull(inspection.originalBootKey)
        assertNull(inspection.originalBootHash)
    }

    private fun writeOptionalI32(
        bytes: ByteArray,
        offset: Int,
        value: Int?,
    ) {
        if (value == null) return
        bytes[offset] = 1
        writeI32(bytes, offset + 1, value)
    }

    private fun writeU16(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun writeI32(
        bytes: ByteArray,
        offset: Int,
        value: Int,
    ) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}
