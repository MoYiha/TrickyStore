package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Shared attestation identifiers must stay confined to explicitly chosen
 * targets: any app rule, identity targeting, a matched assignment with
 * attestation resolved on, or the dedicated blanket opt-in. The top-level
 * toggle alone never selects a uid.
 */
class AttestationTargetScopeTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var configDir: File

    @Before
    fun setUp() {
        Config.reset()
        PolicyState.resetForTesting()
        SecureFile.impl = MockSecureFileOperations()
        configDir = tempFolder.newFolder("config")
        Config.setRootForTesting(configDir)
        PolicyState.setRootForTesting(configDir)
    }

    @After
    fun tearDown() {
        Config.reset()
        PolicyState.resetForTesting()
        SecureFile.impl = SecureFile.DefaultSecureFileOperations()
    }

    private fun enableMarker(name: String) {
        File(configDir, name).createNewFile()
        Config.refreshRuntimeSetting(name)
    }

    private fun setAttestationIds(vararg pairs: Pair<String, ByteArray>) {
        val field = Config::class.java.getDeclaredField("attestationIds")
        field.isAccessible = true
        field.set(Config, pairs.toMap())
    }

    @Test
    fun `legacy inherit without selection stays genuine`() {
        enableMarker("spoof_enabled")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_001, arrayOf("com.example.app"))

        assertNull(Config.getAttestationId("IMEI", 13_001))
    }

    @Test
    fun `legacy template rule keeps per-app values`() {
        File(configDir, "app_config").apply { writeText("com.example.app pixel8pro null inherit\n") }
            .also { Config.updateAppConfigs(it) }
        enableMarker("spoof_enabled")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_002, arrayOf("com.example.app"))

        val value = Config.getAttestationId("IMEI", 13_002)
        assertNotNull(value)
        assertEquals("355000000000001", String(requireNotNull(value)))
    }

    @Test
    fun `legacy identity target list keeps working`() {
        File(configDir, "identity_target.txt").apply { writeText("com.example.app\n") }
        Config.refreshRuntimeSetting("identity_target.txt")
        enableMarker("spoof_enabled")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_003, arrayOf("com.example.app"))

        val value = Config.getAttestationId("IMEI", 13_003)
        assertNotNull(value)
        assertEquals("355000000000001", String(requireNotNull(value)))
    }

    @Test
    fun `v2 top-level toggle alone never selects an unassigned uid`() {
        installV2(attestation = true, profiles = JSONArray())
        enableMarker("global_mode")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_004, arrayOf("com.example.app"))

        assertNull(Config.getAttestationId("IMEI", 13_004))
    }

    @Test
    fun `v2 assigned profile with resolved attestation applies`() {
        installV2(
            attestation = false,
            profiles =
                JSONArray().put(
                    profile(
                        "Bank",
                        arrayOf("com.example.bank"),
                        features = JSONObject().put("attestationIdentity", true),
                    ),
                ),
        )
        enableMarker("global_mode")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_005, arrayOf("com.example.bank"))

        val value = Config.getAttestationId("IMEI", 13_005)
        assertNotNull(value)
        assertEquals("355000000000001", String(requireNotNull(value)))
    }

    @Test
    fun `v2 explicit false override opts out`() {
        installV2(
            attestation = true,
            profiles =
                JSONArray().put(
                    profile(
                        "Quiet",
                        arrayOf("com.example.quiet"),
                        features = JSONObject().put("attestationIdentity", false),
                    ),
                ),
        )
        enableMarker("global_mode")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_006, arrayOf("com.example.quiet"))

        assertNull(Config.getAttestationId("IMEI", 13_006))
    }

    @Test
    fun `v2 blanket opt-in restores shared identifiers`() {
        installV2(attestation = false, profiles = JSONArray())
        enableMarker("global_mode")
        enableMarker("global_attestation_mode")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_007, arrayOf("com.example.app"))

        val value = Config.getAttestationId("IMEI", 13_007)
        assertNotNull(value)
        assertEquals("355000000000001", String(requireNotNull(value)))
    }

    @Test
    fun `protected infrastructure never receives identifiers`() {
        installV2(attestation = true, profiles = JSONArray())
        enableMarker("global_mode")
        enableMarker("global_attestation_mode")
        setAttestationIds("IMEI" to "355000000000001".toByteArray(Charsets.UTF_8))
        Config.setPackagesForTesting(13_008, arrayOf("com.android.rkpdapp"))

        assertNull(Config.getAttestationId("IMEI", 13_008))
    }

    private fun installV2(
        attestation: Boolean,
        profiles: JSONArray,
    ) {
        val features =
            JSONObject()
                .put("buildIdentity", false)
                .put("attestationIdentity", attestation)
                .put("telephonyIdentity", false)
                .put("regionIdentity", false)
                .put("identityRefresh", false)
                .put("securityPatch", false)
        val state =
            JSONObject()
                .put("version", PolicyState.SCHEMA_VERSION)
                .put("features", features)
                .put(
                    "securityPatch",
                    JSONObject()
                        .put("automaticThresholdMonths", 6)
                        .put("system", JSONObject().put("mode", "device_default"))
                        .put("vendor", JSONObject().put("mode", "device_default"))
                        .put("boot", JSONObject().put("mode", "device_default")),
                ).put("profiles", profiles)
                .put("activeProfile", JSONObject.NULL)
        PolicyState.installStateForTesting(state.toString())
    }

    private fun profile(
        name: String,
        applications: Array<String>,
        features: JSONObject,
    ): JSONObject =
        JSONObject()
            .put("name", name)
            .put("applications", JSONArray(applications.toList()))
            .put("privacy", "inherit")
            .put("features", features)
            .put("securityPatch", JSONObject())
}
