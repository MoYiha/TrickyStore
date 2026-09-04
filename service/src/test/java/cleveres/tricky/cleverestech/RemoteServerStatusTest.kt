package cleveres.tricky.cleverestech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteServerStatusTest {
    @Test
    fun `401 explains expired or invalid api key`() {
        val status = RemoteServerStatus.fromHttp(401, null)
        assertTrue(status.startsWith("AUTH_ERROR:"))
        assertTrue(status.contains("expired"))
        assertTrue(status.length <= 128)
    }

    @Test
    fun `403 explains access denial and temporary ban retry timing`() {
        val generic = RemoteServerStatus.fromHttp(403, null)
        assertTrue(generic.startsWith("ACCESS_DENIED:"))
        assertTrue(generic.contains("temporarily banned"))
        assertTrue(generic.length <= 128)

        assertEquals(
            "ACCESS_DENIED: API key temporarily banned. Retry after 2592000 seconds.",
            RemoteServerStatus.fromHttp(403, "2592000"),
        )
        assertFalse(RemoteServerStatus.fromHttp(403, "malicious-value").contains("malicious-value"))
    }

    @Test
    fun `429 includes bounded numeric retry after`() {
        assertEquals(
            "RATE_LIMITED: Too many requests. Retry after 60 seconds.",
            RemoteServerStatus.fromHttp(429, "60"),
        )
        assertFalse(RemoteServerStatus.fromHttp(429, "not-a-number").contains("not-a-number"))
        assertFalse(RemoteServerStatus.fromHttp(429, "999999999").contains("999999999"))
    }

    @Test
    fun `503 distinguishes provider availability`() {
        val status = RemoteServerStatus.fromHttp(503, null)
        assertTrue(status.startsWith("SERVICE_UNAVAILABLE:"))
        assertTrue(status.contains("eligible keybox"))
        assertTrue(status.length <= 128)
    }
}
