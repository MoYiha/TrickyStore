package cleveres.tricky.encryptor

import java.nio.ByteBuffer

internal object CboxSignatureV2 {
    private val domain = "CBOX-SIGNATURE-V2\u0000".toByteArray(Charsets.US_ASCII)

    fun update(
        authorUtf8: ByteArray,
        xmlUtf8: ByteArray,
        sink: (ByteArray) -> Unit,
    ) {
        val authorLength = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(authorUtf8.size).array()
        val xmlLength = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(xmlUtf8.size).array()
        try {
            sink(domain)
            sink(authorLength)
            sink(authorUtf8)
            sink(xmlLength)
            sink(xmlUtf8)
        } finally {
            authorLength.fill(0)
            xmlLength.fill(0)
        }
    }
}
