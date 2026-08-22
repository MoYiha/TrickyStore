package cleveres.tricky.cleverestech

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidJsonIdentityContractTest {
    @Test
    fun `platform JSON null cannot alias literal null profile name`() {
        val profile =
            JSONObject()
                .put("name", "null")
                .put("features", JSONObject().put("buildIdentity", true).put("regionIdentity", true))
        val state =
            JSONObject()
                .put("source", "v2")
                .put(
                    "features",
                    JSONObject()
                        .put("buildIdentity", false)
                        .put("attestationIdentity", false)
                        .put("telephonyIdentity", false)
                        .put("regionIdentity", false)
                        .put("identityRefresh", false)
                        .put("securityPatch", false),
                )
                .put("profiles", JSONArray().put(profile))
                .put("activeProfile", JSONObject.NULL)

        // This is the Android platform behavior that the host org.json test dependency
        // does not reproduce. Keep it in a real instrumentation test so CI detects
        // platform/library semantic drift instead of silently trusting a false oracle.
        assertTrue(state.isNull("activeProfile"))
        assertEquals("null", state.optString("activeProfile"))

        val inactive = LegacyIdentityMarkers.desiredState(state)
        assertFalse(inactive.engine)
        assertFalse(inactive.build)
        assertFalse(inactive.region)

        state.put("activeProfile", "null")
        val active = LegacyIdentityMarkers.desiredState(state)
        assertTrue(active.engine)
        assertTrue(active.build)
        assertTrue(active.region)
    }
}
