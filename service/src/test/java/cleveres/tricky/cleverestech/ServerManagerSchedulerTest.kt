package cleveres.tricky.cleverestech

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class ServerManagerSchedulerTest {
    @Test
    fun `scheduled keybox maintenance depends only on server refresh policy`() {
        val now = TimeUnit.HOURS.toMillis(100)
        val due = server("due", priority = 20, lastChecked = now - TimeUnit.HOURS.toMillis(2))
        val first = server("first", priority = 10, lastChecked = 0)
        val recent = server("recent", priority = 1, lastChecked = now - TimeUnit.MINUTES.toMillis(30))
        val manual = server("manual", priority = 2, lastChecked = 0, autoRefresh = false)
        val disabled = server("disabled", priority = 3, lastChecked = 0, enabled = false)

        val selected =
            ServerManager.selectDueServersForRefresh(
                listOf(due, recent, disabled, first, manual),
                now,
            )

        // Identity/Spoof Engine state is intentionally not an input to core keybox maintenance.
        assertEquals(listOf("first", "due"), selected.map { it.id })
    }

    private fun server(
        id: String,
        priority: Int,
        lastChecked: Long,
        autoRefresh: Boolean = true,
        enabled: Boolean = true,
    ) =
        ServerManager.ServerConfig(
            id = id,
            name = id,
            url = "https://example.com/$id",
            priority = priority,
            enabled = enabled,
            authType = "NONE",
            authData = JSONObject(),
            autoRefresh = autoRefresh,
            refreshIntervalHours = 1,
            lastChecked = lastChecked,
        )
}
