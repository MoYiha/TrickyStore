package cleveres.tricky.cleverestech

import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class ServerHeaderValidationTest {
    @Test
    fun `Android-forbidden control characters are rejected before fetch`() {
        val configurations =
            listOf(
                "BEARER" to JSONObject().put("token", "token\u0000value"),
                "API_KEY" to
                    JSONObject()
                        .put("headerName", "X-API-Key")
                        .put("key", "key\tvalue"),
                "CUSTOM" to
                    JSONObject().put(
                        "headers",
                        JSONObject().put("X-Custom", "value\u007f"),
                    ),
            )

        configurations.forEach { (authType, authData) ->
            assertThrows(IllegalArgumentException::class.java) {
                ServerManager.validateServer(server(authType, authData))
            }
        }
    }

    @Test
    fun `visible Unicode header values remain supported`() {
        ServerManager.validateServer(
            server(
                "CUSTOM",
                JSONObject().put("headers", JSONObject().put("X-Custom", "geçerli-🍩")),
            ),
        )
    }

    private fun server(
        authType: String,
        authData: JSONObject,
    ) =
        ServerManager.ServerConfig(
            id = "header-validation-test",
            name = "Header validation test",
            url = "https://example.com/keybox.cbox",
            priority = 0,
            enabled = true,
            authType = authType,
            authData = authData,
            autoRefresh = false,
            refreshIntervalHours = 24,
        )
}
