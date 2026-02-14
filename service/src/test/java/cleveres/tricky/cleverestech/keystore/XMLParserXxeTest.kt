package cleveres.tricky.cleverestech.keystore

import org.junit.Assert.fail
import org.junit.Test
import java.io.StringReader

class XMLParserXxeTest {

    @Test
    fun testDtdIsStrictlyRejected() {
        // Simple DTD without entities.
        // If parser ignores DTD (feature=false), it might skip this or report DOCDECL.
        // We want it to REPORT DOCDECL so we can reject it.
        val xml = """
            <!DOCTYPE foo [
              <!ELEMENT foo ANY >
            ]>
            <root>hello</root>
        """.trimIndent()

        try {
            XMLParser(StringReader(xml))
            fail("Parsing should have failed due to DTD presence!")
        } catch (e: SecurityException) {
            // Success! The parser explicitly rejected the DTD.
            if (e.message?.contains("DTD is not allowed") != true) {
                fail("Threw SecurityException but with unexpected message: ${e.message}")
            }
        } catch (e: Exception) {
             val msg = e.message ?: ""
             // If parser throws exception on DTD, that's also fine (blocked)
             if (msg.contains("docdecl not permitted")) {
                 return
             }
             // If it failed for another reason (e.g. malformed), rethrow to fail test
             throw e
        }
    }
}
