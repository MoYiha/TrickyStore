package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.KeyboxVerifier
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboxPriorityOrderTest {

    @Test
    fun `category mapping resolves all valid security levels`() {
        assertEquals(
            KeyboxPriorityCategory.VALID_RKP,
            KeyboxPriorityCategory.fromValidityAndLevel(KeyboxVerifier.ValidityState.VALID, null, "RKP"),
        )
        assertEquals(
            KeyboxPriorityCategory.VALID_STRONGBOX,
            KeyboxPriorityCategory.fromValidityAndLevel(KeyboxVerifier.ValidityState.VALID, null, "StrongBox"),
        )
        assertEquals(
            KeyboxPriorityCategory.VALID_TEE,
            KeyboxPriorityCategory.fromValidityAndLevel(KeyboxVerifier.ValidityState.VALID, null, "TEE"),
        )
        assertEquals(
            KeyboxPriorityCategory.VALID_UNKNOWN,
            KeyboxPriorityCategory.fromValidityAndLevel(KeyboxVerifier.ValidityState.VALID, null, "Unknown"),
        )
    }

    @Test
    fun `category mapping resolves invalid sub-reasons accurately`() {
        assertEquals(
            KeyboxPriorityCategory.INVALID_EXPIRED_RKP,
            KeyboxPriorityCategory.fromValidityAndLevel(
                KeyboxVerifier.ValidityState.INVALID,
                KeyboxVerifier.InvalidReason.EXPIRED,
                "RKP",
            ),
        )
        assertEquals(
            KeyboxPriorityCategory.INVALID_REVOKED_STRONGBOX,
            KeyboxPriorityCategory.fromValidityAndLevel(
                KeyboxVerifier.ValidityState.INVALID,
                KeyboxVerifier.InvalidReason.REVOKED,
                "StrongBox",
            ),
        )
        assertEquals(
            KeyboxPriorityCategory.INVALID_VERIFICATION_FAILED_TEE,
            KeyboxPriorityCategory.fromValidityAndLevel(
                KeyboxVerifier.ValidityState.INVALID,
                KeyboxVerifier.InvalidReason.VERIFICATION_FAILED,
                "TEE",
            ),
        )
        assertEquals(
            KeyboxPriorityCategory.INVALID_VERIFICATION_FAILED_UNKNOWN,
            KeyboxPriorityCategory.fromValidityAndLevel(
                KeyboxVerifier.ValidityState.INVALID,
                null,
                "other",
            ),
        )
    }

    @Test
    fun `default preference serialization roundtrip`() {
        val pref = KeyboxPriorityPreference.DEFAULT
        val json = pref.toJson()
        assertEquals("default", json.getString("mode"))

        val restored = KeyboxPriorityPreference.fromJson(json)
        assertEquals(KeyboxPriorityPreference.Mode.DEFAULT, restored.mode)
        assertEquals(KeyboxPriorityCategory.DEFAULT_ORDER, restored.effectiveOrder())
    }

    @Test
    fun `custom preference serialization roundtrip`() {
        val customOrder = KeyboxPriorityCategory.DEFAULT_ORDER.reversed()
        val pref = KeyboxPriorityPreference(KeyboxPriorityPreference.Mode.CUSTOM, customOrder)
        val json = pref.toJson()
        assertEquals("custom", json.getString("mode"))

        val restored = KeyboxPriorityPreference.fromJson(json)
        assertEquals(KeyboxPriorityPreference.Mode.CUSTOM, restored.mode)
        assertEquals(customOrder, restored.effectiveOrder())
    }

    @Test
    fun `custom UI twelve-permutation is accepted and expanded deterministically`() {
        val uiOrder = KeyboxPriorityCategory.UI_ORDER
        assertEquals(12, uiOrder.size)
        val submitted = uiOrder.reversed()
        val json = JSONObject().apply {
            put("mode", "custom")
            put("customOrder", JSONArray(submitted.map { it.name }))
        }
        val restored = KeyboxPriorityPreference.fromJson(json)
        assertEquals(KeyboxPriorityPreference.Mode.CUSTOM, restored.mode)
        assertEquals(submitted, restored.customOrder)

        val effective = restored.effectiveOrder()
        assertEquals(32, effective.size)
        assertEquals(submitted, effective.take(12))
        assertEquals(
            KeyboxPriorityCategory.DEFAULT_ORDER.filter { it !in submitted },
            effective.drop(12),
        )
    }

    @Test
    fun `legacy six-permutation migrates to local-server pairs`() {
        val legacy = KeyboxPriorityCategory.LEGACY_UI_ORDER
        assertEquals(6, legacy.size)
        val submitted = legacy.reversed()
        val json = JSONObject().apply {
            put("mode", "custom")
            put("customOrder", JSONArray(submitted.map { it.name }))
        }
        val restored = KeyboxPriorityPreference.fromJson(json)
        assertEquals(KeyboxPriorityPreference.Mode.CUSTOM, restored.mode)
        val expected = submitted.flatMap { category ->
            listOf(category, KeyboxPriorityCategory.valueOf("${category.name}_SERVER"))
        }
        assertEquals(expected, restored.customOrder)
        assertEquals(12, restored.customOrder.size)
        val effective = restored.effectiveOrder()
        assertEquals(32, effective.size)
        assertEquals(expected, effective.take(12))
    }

    @Test
    fun `legacy sixteen-permutation migrates to thirty-two`() {
        val legacy = KeyboxPriorityCategory.LEGACY_FULL_ORDER
        assertEquals(16, legacy.size)
        val submitted = legacy.reversed()
        val json = JSONObject().apply {
            put("mode", "custom")
            put("customOrder", JSONArray(submitted.map { it.name }))
        }
        val restored = KeyboxPriorityPreference.fromJson(json)
        assertEquals(KeyboxPriorityPreference.Mode.CUSTOM, restored.mode)
        assertEquals(32, restored.customOrder.size)
        val expected = submitted.flatMap { category ->
            listOf(category, KeyboxPriorityCategory.valueOf("${category.name}_SERVER"))
        }
        assertEquals(expected, restored.customOrder)
        assertEquals(expected, restored.effectiveOrder())
    }

    @Test
    fun `origin-aware mapping separates local and server tiers`() {
        assertEquals(
            KeyboxPriorityCategory.VALID_RKP,
            KeyboxPriorityCategory.fromValidityLevelAndOrigin(KeyboxVerifier.ValidityState.VALID, null, "RKP", false),
        )
        assertEquals(
            KeyboxPriorityCategory.VALID_RKP_SERVER,
            KeyboxPriorityCategory.fromValidityLevelAndOrigin(KeyboxVerifier.ValidityState.VALID, null, "RKP", true),
        )
        assertEquals(
            KeyboxPriorityCategory.VALID_TEE_SERVER,
            KeyboxPriorityCategory.fromValidityLevelAndOrigin(KeyboxVerifier.ValidityState.VALID, null, "TEE", true),
        )
        assertEquals(
            KeyboxPriorityCategory.INVALID_REVOKED_TEE_SERVER,
            KeyboxPriorityCategory.fromValidityLevelAndOrigin(
                KeyboxVerifier.ValidityState.INVALID,
                KeyboxVerifier.InvalidReason.REVOKED,
                "TEE",
                true,
            ),
        )
    }

    @Test
    fun `server filename prefix marks server origin`() {
        assertEquals(true, KeyboxPriorityCategory.isServerKeyboxFilename("server_feed.xml"))
        assertEquals(true, KeyboxPriorityCategory.isServerKeyboxFilename("server_feed.cbox"))
        assertEquals(false, KeyboxPriorityCategory.isServerKeyboxFilename("keybox.xml"))
        assertEquals(false, KeyboxPriorityCategory.isServerKeyboxFilename("Server_feed.xml"))
    }

    @Test
    fun `default order keeps local before server within each group`() {
        val order = KeyboxPriorityCategory.DEFAULT_ORDER
        assertEquals(32, order.size)
        var index = 0
        while (index < order.size) {
            val local = order[index]
            val server = order[index + 1]
            assertEquals(false, local.name.endsWith("_SERVER"))
            assertEquals("${local.name}_SERVER", server.name)
            index += 2
        }
        val ui = KeyboxPriorityCategory.UI_ORDER
        assertEquals(12, ui.size)
        assertEquals("VALID_RKP", ui.first().name)
        assertEquals("VALID_RKP_SERVER", ui[1].name)
    }

    @Test
    fun `custom preference falls back to default on incomplete order`() {
        val incompleteJson = JSONObject().apply {
            put("mode", "custom")
            put("customOrder", JSONArray(KeyboxPriorityCategory.DEFAULT_ORDER.dropLast(1).map { it.name }))
        }
        val restored = KeyboxPriorityPreference.fromJson(incompleteJson)
        assertEquals(KeyboxPriorityPreference.DEFAULT, restored)
    }

    @Test
    fun `custom preference falls back to default on unknown category`() {
        val unknownOrder = KeyboxPriorityCategory.DEFAULT_ORDER.map { it.name }.toMutableList()
        unknownOrder[unknownOrder.lastIndex] = "VALID_FUTURE_CATEGORY"
        val unknownJson = JSONObject().apply {
            put("mode", "custom")
            put("customOrder", JSONArray(unknownOrder))
        }
        val restored = KeyboxPriorityPreference.fromJson(unknownJson)
        assertEquals(KeyboxPriorityPreference.DEFAULT, restored)
    }

    @Test
    fun `custom preference falls back to default on duplicate category`() {
        val duplicateOrder = KeyboxPriorityCategory.DEFAULT_ORDER.map { it.name }.toMutableList()
        duplicateOrder[duplicateOrder.lastIndex] = duplicateOrder.first()
        val duplicateJson = JSONObject().apply {
            put("mode", "custom")
            put("customOrder", JSONArray(duplicateOrder))
        }
        val restored = KeyboxPriorityPreference.fromJson(duplicateJson)
        assertEquals(KeyboxPriorityPreference.DEFAULT, restored)
    }

    @Test
    fun `null or empty json falls back to default`() {
        val restoredNull = KeyboxPriorityPreference.fromJson(null)
        assertEquals(KeyboxPriorityPreference.DEFAULT, restoredNull)

        val restoredEmpty = KeyboxPriorityPreference.fromJson(JSONObject())
        assertEquals(KeyboxPriorityPreference.DEFAULT, restoredEmpty)
    }

    @Test
    fun `filterTopPriorityTier returns only highest available priority category`() {
        val kbRkp = MockKeyBox("rkp", "RKP")
        val kbTee = MockKeyBox("tee", "TEE")
        val kbSb = MockKeyBox("sb", "StrongBox")

        val pool = listOf(kbTee, kbRkp, kbSb)
        val filtered = KeyboxPriorityOrder.filterTopPriorityTier(
            pool,
            KeyboxPriorityCategory.DEFAULT_ORDER,
        ) { kb ->
            when (kb.id) {
                "rkp" -> KeyboxPriorityCategory.VALID_RKP
                "sb" -> KeyboxPriorityCategory.VALID_STRONGBOX
                else -> KeyboxPriorityCategory.VALID_TEE
            }
        }

        // VALID_RKP is higher than STRONGBOX and TEE in default order
        assertEquals(1, filtered.size)
        assertEquals("rkp", filtered[0].id)
    }

    @Test
    fun `server tier is distinct from local tier in custom order`() {
        val localTee = MockKeyBox("local-tee", "TEE")
        val serverRkp = MockKeyBox("server-rkp", "RKP")
        val order = listOf(
            KeyboxPriorityCategory.VALID_RKP_SERVER,
            KeyboxPriorityCategory.VALID_TEE,
        )
        val filtered = KeyboxPriorityOrder.filterTopPriorityTier(
            listOf(localTee, serverRkp),
            KeyboxPriorityCategory.expandToFullOrder(order),
        ) { kb ->
            if (kb.id.startsWith("server")) KeyboxPriorityCategory.VALID_RKP_SERVER
            else KeyboxPriorityCategory.VALID_TEE
        }
        assertEquals(1, filtered.size)
        assertEquals("server-rkp", filtered[0].id)
    }

    @Test
    fun `filterTopPriorityTier keeps all items in same top tier`() {
        val kbRkp1 = MockKeyBox("rkp1", "RKP")
        val kbRkp2 = MockKeyBox("rkp2", "RKP")
        val kbTee = MockKeyBox("tee", "TEE")

        val pool = listOf(kbRkp1, kbTee, kbRkp2)
        val filtered = KeyboxPriorityOrder.filterTopPriorityTier(
            pool,
            KeyboxPriorityCategory.DEFAULT_ORDER,
        ) { kb ->
            if (kb.id.startsWith("rkp")) KeyboxPriorityCategory.VALID_RKP
            else KeyboxPriorityCategory.VALID_TEE
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.contains(kbRkp1))
        assertTrue(filtered.contains(kbRkp2))
    }

    private data class MockKeyBox(val id: String, val level: String)
}
