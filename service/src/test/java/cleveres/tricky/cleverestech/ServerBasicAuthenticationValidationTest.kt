package cleveres.tricky.cleverestech

import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerBasicAuthenticationValidationTest {
    @Test
    fun `multibyte credentials that overflow the authorization header are rejected`() {
        val username = "\u0800".repeat(1024)
        val password = "\u0800".repeat(1024)

        assertThrows(IllegalArgumentException::class.java) {
            ServerManager.validateServer(server(username, password))
        }
    }

    @Test
    fun `username containing the basic authentication separator is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ServerManager.validateServer(server("user:name", "password"))
        }
    }

    @Test
    fun `multibyte credentials fitting the encoded header bound are accepted`() {
        val username = "\u0800".repeat(1022)
        val password = "\u0800".repeat(1023)

        ServerManager.validateServer(server(username, password))
    }

    private fun server(
        username: String,
        password: String,
    ) =
        ServerManager.ServerConfig(
            id = "basic-validation-test",
            name = "Basic validation test",
            url = "https://example.com/keybox.cbox",
            priority = 0,
            enabled = true,
            authType = "BASIC",
            authData =
                JSONObject()
                    .put("username", username)
                    .put("password", password),
            autoRefresh = false,
            refreshIntervalHours = 24,
        )
}
