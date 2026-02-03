package cleveres.tricky.cleverestech.util

import cleveres.tricky.cleverestech.Logger
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class KeyboxVerifierTest {

    @Before
    fun setup() {
        Logger.setImpl(object : Logger.LogImpl {
            override fun d(tag: String, msg: String) {}
            override fun e(tag: String, msg: String) {}
            override fun e(tag: String, msg: String, t: Throwable?) {}
            override fun i(tag: String, msg: String) {}
        })
    }

    @Test
    fun parseCrl_shouldThrowOnInvalidJson() {
        try {
            KeyboxVerifier.parseCrl("{ invalid_json }")
            fail("Should have thrown exception on invalid JSON")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun parseCrl_shouldThrowOnMissingEntries() {
        try {
            KeyboxVerifier.parseCrl("{}")
            fail("Should have thrown exception on missing entries")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun parseCrl_validJson_decimal() {
        val json = """
            {
              "entries": {
                "12345": { "status": "REVOKED" }
              }
            }
        """
        val result = KeyboxVerifier.parseCrl(json)
        // 12345 (dec) -> 3039 (hex)
        assertTrue("Should contain hex of 12345", result.contains("3039"))
    }

    @Test
    fun parseCrl_validJson_hex() {
        val json = """
            {
              "entries": {
                "c35747a084470c3135aeefe2b8d40cd6": { "status": "REVOKED" }
              }
            }
        """
        val result = KeyboxVerifier.parseCrl(json)
        assertTrue("Should contain hex string", result.contains("c35747a084470c3135aeefe2b8d40cd6"))
    }

    @Test
    fun parseCrl_mixed() {
        val json = """
            {
              "entries": {
                "12345": { "status": "REVOKED" },
                "aabbcc": { "status": "REVOKED" }
              }
            }
        """
        val result = KeyboxVerifier.parseCrl(json)
        assertTrue(result.contains("3039"))
        assertTrue(result.contains("aabbcc"))
    }
}
