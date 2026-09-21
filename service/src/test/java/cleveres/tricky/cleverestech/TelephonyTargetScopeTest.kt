package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Telephony spoofing must stay confined to explicitly chosen targets. The
 * top-level toggle remains the master switch for hook registration, but it
 * alone never selects a uid; otherwise enabling telephony under global mode
 * sprays one shared subscriber identity across every app, including carrier
 * and provisioning packages that must keep genuine values. The explicit
 * global-telephony opt-in restores blanket coverage deliberately.
 */
class TelephonyTargetScopeTest {
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

    @Test
    fun `legacy toggle alone never selects a uid`() {
        enableMarker("spoof_enabled")
        enableMarker("telephony")
        enableMarker("global_mode")
        Config.setPackagesForTesting(12_001, arrayOf("com.example.app"))

        assertFalse(Config.shouldApplyTelephonyPrivacy(12_001))
    }

    @Test
    fun `legacy isolate rule stays scoped with toggle off`() {
        writeRules("com.example.app null null isolate\n")
        enableMarker("spoof_enabled")
        Config.setPackagesForTesting(12_002, arrayOf("com.example.app"))

        assertTrue(Config.shouldApplyTelephonyPrivacy(12_002))
    }

    @Test
    fun `legacy redact rule stays scoped`() {
        writeRules("com.example.app null null redact\n")
        enableMarker("spoof_enabled")
        enableMarker("telephony")
        Config.setPackagesForTesting(12_003, arrayOf("com.example.app"))

        assertTrue(Config.shouldApplyTelephonyPrivacy(12_003))
    }

    @Test
    fun `legacy untargeted uid stays genuine`() {
        writeRules("com.example.app null null isolate\n")
        enableMarker("spoof_enabled")
        enableMarker("telephony")
        Config.setPackagesForTesting(12_004, arrayOf("com.example.other"))

        assertFalse(Config.shouldApplyTelephonyPrivacy(12_004))
    }

    @Test
    fun `global telephony opt-in covers targeted uids`() {
        enableMarker("spoof_enabled")
        enableMarker("telephony")
        enableMarker("global_mode")
        enableMarker("global_telephony_mode")
        Config.setPackagesForTesting(12_005, arrayOf("com.example.app"))

        assertTrue(Config.shouldApplyTelephonyPrivacy(12_005))
    }

    @Test
    fun `global telephony opt-in stays inside targeting`() {
        enableMarker("spoof_enabled")
        enableMarker("telephony")
        enableMarker("global_telephony_mode")
        Config.setPackagesForTesting(12_006, arrayOf("com.example.app"))

        assertFalse(Config.shouldApplyTelephonyPrivacy(12_006))
    }

    @Test
    fun `top-level toggle alone never selects an unassigned uid`() {
        installV2(telephony = true, profiles = JSONArray())
        enableMarker("global_mode")
        Config.setPackagesForTesting(12_007, arrayOf("com.example.app"))

        assertTrue(Config.shouldInterceptTelephony)
        assertFalse(Config.shouldApplyTelephonyPrivacy(12_007))
    }

    @Test
    fun `assigned profile with resolved telephony applies`() {
        installV2(
            telephony = false,
            profiles =
                JSONArray().put(
                    profile(
                        "Bank",
                        arrayOf("com.example.bank"),
                        features = JSONObject().put("telephonyIdentity", true),
                    ),
                ),
        )
        enableMarker("global_mode")
        Config.setPackagesForTesting(12_008, arrayOf("com.example.bank"))

        assertTrue(Config.shouldApplyTelephonyPrivacy(12_008))
    }

    @Test
    fun `assigned profile without telephony key inherits top-level true`() {
        installV2(
            telephony = true,
            profiles =
                JSONArray().put(
                    profile(
                        "Bank",
                        arrayOf("com.example.bank"),
                        features = JSONObject(),
                    ),
                ),
        )
        enableMarker("global_mode")
        Config.setPackagesForTesting(12_009, arrayOf("com.example.bank"))

        assertTrue(Config.shouldApplyTelephonyPrivacy(12_009))
    }

    @Test
    fun `explicit false override opts out despite top-level true`() {
        installV2(
            telephony = true,
            profiles =
                JSONArray().put(
                    profile(
                        "Quiet",
                        arrayOf("com.example.quiet"),
                        features = JSONObject().put("telephonyIdentity", false),
                    ),
                ),
        )
        enableMarker("global_mode")
        Config.setPackagesForTesting(12_010, arrayOf("com.example.quiet"))

        assertFalse(Config.shouldApplyTelephonyPrivacy(12_010))
    }

    @Test
    fun `non-inherit privacy rule applies without profile`() {
        writeRules("com.example.app null null redact\n")
        installV2(telephony = false, profiles = JSONArray())
        Config.setPackagesForTesting(12_011, arrayOf("com.example.app"))

        assertTrue(Config.shouldApplyTelephonyPrivacy(12_011))
    }

    @Test
    fun `uid without packages is never selected`() {
        installV2(
            telephony = true,
            profiles =
                JSONArray().put(
                    profile(
                        "Bank",
                        arrayOf("com.example.bank"),
                        features = JSONObject().put("telephonyIdentity", true),
                    ),
                ),
        )
        enableMarker("global_mode")
        Config.setPackagesForTesting(12_012, arrayOf())

        assertFalse(Config.shouldApplyTelephonyPrivacy(12_012))
    }

    @Test
    fun `global telephony opt-in covers assigned uids without global mode`() {
        installV2(
            telephony = false,
            profiles =
                JSONArray().put(
                    profile(
                        "Bank",
                        arrayOf("com.example.bank"),
                        features = JSONObject().put("telephonyIdentity", true),
                        template = "pixel8pro",
                    ),
                ),
        )
        enableMarker("global_telephony_mode")
        Config.setPackagesForTesting(12_013, arrayOf("com.example.bank"))

        assertTrue(Config.shouldApplyTelephonyPrivacy(12_013))
    }

    private fun writeRules(text: String) {
        val rules = File(configDir, "app_config").apply { writeText(text) }
        Config.updateAppConfigs(rules)
    }

    private fun installV2(
        telephony: Boolean,
        profiles: JSONArray,
    ) {
        val features =
            JSONObject()
                .put("buildIdentity", false)
                .put("attestationIdentity", false)
                .put("telephonyIdentity", telephony)
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
        template: String? = null,
    ): JSONObject {
        val json =
            JSONObject()
                .put("name", name)
                .put("applications", JSONArray(applications.toList()))
                .put("privacy", "inherit")
                .put("features", features)
                .put("securityPatch", JSONObject())
        if (template != null) json.put("template", template)
        return json
    }
}
