package cleveres.tricky.cleverestech.keystore

import cleveres.tricky.cleverestech.Logger
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KeyboxMultiKeyGoldenOracleTest {
    @Before
    fun silenceLogger() {
        Logger.setImpl(
            object : Logger.LogImpl {
                override fun d(tag: String, msg: String) = Unit

                override fun e(tag: String, msg: String) = Unit

                override fun e(tag: String, msg: String, t: Throwable?) = Unit

                override fun i(tag: String, msg: String) = Unit
            },
        )
    }

    @Test
    fun `one XML Keybox with EC and RSA keys flattens to two managed KeyBoxes`() {
        val ec = readFixture("/keybox/valid_ec.xml")
        val rsa = readFixture("/keybox/valid_rsa.xml")
        val xml =
            "<AndroidAttestation><NumberOfKeyboxes>1</NumberOfKeyboxes><Keybox>" +
                keyElement(ec) +
                keyElement(rsa) +
                "</Keybox></AndroidAttestation>"

        val parsed = CertHack.parseKeyboxXml(StringReader(xml), "multi.xml")
        assertEquals(2, parsed.size)
        assertEquals(listOf("EC", "RSA"), parsed.map { normalizeAlgorithm(it.keyPair().public.algorithm) })
        assertEquals(listOf("multi.xml", "multi.xml"), parsed.map { it.filename() })
    }

    private fun normalizeAlgorithm(algorithm: String): String =
        if (algorithm.equals("EC", ignoreCase = true) || algorithm.equals("ECDSA", ignoreCase = true)) {
            "EC"
        } else {
            algorithm.uppercase()
        }

    private fun readFixture(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)).bufferedReader().use { it.readText() }

    private fun keyElement(xml: String): String {
        val start = xml.indexOf("<Key ")
        val end = xml.indexOf("</Key>", start)
        require(start >= 0 && end >= start)
        return xml.substring(start, end + "</Key>".length)
    }
}
