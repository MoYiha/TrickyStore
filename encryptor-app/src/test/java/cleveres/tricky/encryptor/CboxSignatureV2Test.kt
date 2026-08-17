package cleveres.tricky.encryptor

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class CboxSignatureV2Test {
    @Test
    fun `framing digest matches the managed and Rust v2 oracle`() {
        val author = "Δ-author".toByteArray(Charsets.UTF_8)
        val xml = "<AndroidAttestation NumberOfKeyboxes=\"0\"/>".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        CboxSignatureV2.update(author, xml, digest::update)

        assertEquals(
            "efe36dd07652c45ca0575efeb17e7ebad5ce530de9d3908452270a1a34aa4258",
            digest.digest().joinToString("") { "%02x".format(it) },
        )
        author.fill(0)
        xml.fill(0)
    }
}
