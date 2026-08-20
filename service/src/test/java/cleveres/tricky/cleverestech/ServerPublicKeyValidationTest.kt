package cleveres.tricky.cleverestech

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ServerPublicKeyValidationTest {
    @Test
    fun `server validation and fused transport share the UTF-8 byte limit`() {
        val asciiBoundary = "A".repeat(16 * 1024)
        assertTrue(FusedCboxBackend.isPublicKeyWithinLimit(asciiBoundary))
        ServerManager.validateServer(server(asciiBoundary))

        val multiByteOverLimit = "\u0800".repeat(6_000)
        assertTrue(multiByteOverLimit.length < 16 * 1024)
        assertTrue(multiByteOverLimit.toByteArray(StandardCharsets.UTF_8).size > 16 * 1024)
        assertThrows(IllegalArgumentException::class.java) {
            ServerManager.validateServer(server(multiByteOverLimit))
        }
    }

    @Test
    fun `public key validation rejects one UTF-8 byte over the transport frame bound`() {
        val overLimit = "A".repeat(16 * 1024 + 1)

        assertFalse(FusedCboxBackend.isPublicKeyWithinLimit(overLimit))
        assertThrows(IllegalArgumentException::class.java) {
            ServerManager.validateServer(server(overLimit))
        }
    }

    private fun server(contentPublicKey: String) =
        ServerManager.ServerConfig(
            id = "validation-test",
            name = "Validation test",
            url = "https://example.com/keybox.cbox",
            priority = 0,
            enabled = true,
            authType = "NONE",
            authData = JSONObject(),
            autoRefresh = false,
            refreshIntervalHours = 24,
            contentPublicKey = contentPublicKey,
        )
}
