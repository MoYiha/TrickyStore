package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.CborEncoder
import org.junit.Test
import java.util.LinkedHashMap

class CborEncoderTest {

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    @Test
    fun testCanonicalMapSorting() {
        // "b" (len 1) vs "aa" (len 2)
        // Canonical: "b" < "aa"
        // Java String: "aa" < "b"

        val map = LinkedHashMap<String, Int>()
        map["aa"] = 1
        map["b"] = 2

        // Expected Order: "b", "aa"
        // Encoded:
        // Map(2) (0xa2)
        // "b" (0x61 62) -> 2 (0x02)
        // "aa" (0x62 61 61) -> 1 (0x01)
        // Hex: a2 61 62 02 62 61 61 01

        val expected = "a261620262616101"
        val encoded = CborEncoder.encode(map)
        val encodedHex = bytesToHex(encoded)

        println("Encoded: $encodedHex")

        org.junit.Assert.assertEquals("Expected canonical sort order", expected, encodedHex)
    }

    @Test
    fun testCanonicalMapSortingLongerKeys() {
        // "manufacturer" (12) vs "vb_state" (8)
        // Canonical: "vb_state" < "manufacturer"

        val map = LinkedHashMap<String, Int>()
        map["manufacturer"] = 1
        map["vb_state"] = 2

        // Encoded:
        // Map(2) (0xa2)
        // "vb_state" (8 bytes) -> 0x68 + "vb_state" -> 2
        // "manufacturer" (12 bytes) -> 0x6c + "manufacturer" -> 1

        // 68 76625f7374617465 -> vb_state
        // 02
        // 6c 6d616e756661637475726572 -> manufacturer
        // 01

        val expected = "a26876625f7374617465026c6d616e75666163747572657201"
        val encoded = CborEncoder.encode(map)
        val encodedHex = bytesToHex(encoded)

        println("Encoded: $encodedHex")

        org.junit.Assert.assertEquals("Expected shorter key (vb_state) first", expected, encodedHex)
    }
}
