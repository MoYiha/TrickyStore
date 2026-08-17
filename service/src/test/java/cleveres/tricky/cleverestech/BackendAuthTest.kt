package cleveres.tricky.cleverestech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendAuthTest {
    @Test
    fun `decodeHex accepts exact lowercase 256-bit token`() {
        val encoded = (0 until BackendAuth.TOKEN_BYTES).joinToString("") { "%02x".format(it) }
        val expected = ByteArray(BackendAuth.TOKEN_BYTES) { it.toByte() }
        assertArrayEquals(expected, BackendAuth.decodeHex(encoded))
    }

    @Test
    fun `decodeHex rejects malformed noncanonical and zero tokens`() {
        assertNull(BackendAuth.decodeHex(null))
        assertNull(BackendAuth.decodeHex(""))
        assertNull(BackendAuth.decodeHex("00".repeat(BackendAuth.TOKEN_BYTES - 1)))
        assertNull(BackendAuth.decodeHex("00".repeat(BackendAuth.TOKEN_BYTES + 1)))
        assertNull(BackendAuth.decodeHex("GG".repeat(BackendAuth.TOKEN_BYTES)))
        assertNull(BackendAuth.decodeHex("AA".repeat(BackendAuth.TOKEN_BYTES)))
        assertNull(BackendAuth.decodeHex("00".repeat(BackendAuth.TOKEN_BYTES)))
    }
}
