package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import org.json.JSONArray
import org.json.JSONObject

enum class KeyboxPriorityCategory {
    VALID_RKP,
    VALID_RKP_SERVER,
    VALID_STRONGBOX,
    VALID_STRONGBOX_SERVER,
    VALID_TEE,
    VALID_TEE_SERVER,
    VALID_UNKNOWN,
    VALID_UNKNOWN_SERVER,
    INVALID_EXPIRED_RKP,
    INVALID_EXPIRED_RKP_SERVER,
    INVALID_EXPIRED_STRONGBOX,
    INVALID_EXPIRED_STRONGBOX_SERVER,
    INVALID_EXPIRED_TEE,
    INVALID_EXPIRED_TEE_SERVER,
    INVALID_EXPIRED_UNKNOWN,
    INVALID_EXPIRED_UNKNOWN_SERVER,
    INVALID_REVOKED_RKP,
    INVALID_REVOKED_RKP_SERVER,
    INVALID_REVOKED_STRONGBOX,
    INVALID_REVOKED_STRONGBOX_SERVER,
    INVALID_REVOKED_TEE,
    INVALID_REVOKED_TEE_SERVER,
    INVALID_REVOKED_UNKNOWN,
    INVALID_REVOKED_UNKNOWN_SERVER,
    INVALID_VERIFICATION_FAILED_RKP,
    INVALID_VERIFICATION_FAILED_RKP_SERVER,
    INVALID_VERIFICATION_FAILED_STRONGBOX,
    INVALID_VERIFICATION_FAILED_STRONGBOX_SERVER,
    INVALID_VERIFICATION_FAILED_TEE,
    INVALID_VERIFICATION_FAILED_TEE_SERVER,
    INVALID_VERIFICATION_FAILED_UNKNOWN,
    INVALID_VERIFICATION_FAILED_UNKNOWN_SERVER,
    ;

    companion object {
        val DEFAULT_ORDER: List<KeyboxPriorityCategory> = entries.toList()

        // The twelve categories exposed in the WebUI custom-order list. StrongBox
        // and Unknown levels plus always-blocked verification failures stay valid
        // enum values, but the UI only exposes RKP/TEE combined with the eligible
        // invalid reasons (Valid, Expired, Revoked), each in a local and a server
        // variant. Within one validity/level group the local tier sorts before
        // the server tier, matching the historical stable order where stored and
        // CBOX sources precede remote content.
        val UI_ORDER: List<KeyboxPriorityCategory> =
            listOf(
                VALID_RKP,
                VALID_RKP_SERVER,
                VALID_TEE,
                VALID_TEE_SERVER,
                INVALID_EXPIRED_RKP,
                INVALID_EXPIRED_RKP_SERVER,
                INVALID_EXPIRED_TEE,
                INVALID_EXPIRED_TEE_SERVER,
                INVALID_REVOKED_RKP,
                INVALID_REVOKED_RKP_SERVER,
                INVALID_REVOKED_TEE,
                INVALID_REVOKED_TEE_SERVER,
            )

        // Legacy orders saved before server tiers existed. They contain only the
        // local (unsuffixed) names and are migrated by expanding every entry to
        // its local/server pair, preserving the saved relative order.
        val LEGACY_UI_ORDER: List<KeyboxPriorityCategory> =
            listOf(
                VALID_RKP,
                VALID_TEE,
                INVALID_EXPIRED_RKP,
                INVALID_EXPIRED_TEE,
                INVALID_REVOKED_RKP,
                INVALID_REVOKED_TEE,
            )

        val LEGACY_FULL_ORDER: List<KeyboxPriorityCategory> =
            DEFAULT_ORDER.filter { !it.name.endsWith("_SERVER") }

        const val SERVER_FILENAME_PREFIX = "server_"

        @JvmStatic
        fun isServerKeyboxFilename(filename: String): Boolean = filename.startsWith(SERVER_FILENAME_PREFIX)

        @JvmStatic
        fun isServerKeybox(box: CertHack.KeyBox?): Boolean {
            if (box == null) return false
            return isServerKeyboxFilename(box.filename())
        }

        // Expands a UI twelve-permutation to the full deterministic order by
        // appending the remaining categories in default relative order. Full
        // permutations pass through unchanged. Legacy six/sixteen permutations
        // migrate first by expanding every entry to its local/server pair.
        fun expandToFullOrder(order: List<KeyboxPriorityCategory>): List<KeyboxPriorityCategory> {
            val migrated = migrateLegacyOrder(order)
            return (migrated + DEFAULT_ORDER).distinct()
        }

        internal fun migrateLegacyOrder(order: List<KeyboxPriorityCategory>): List<KeyboxPriorityCategory> {
            if (order.any { it.name.endsWith("_SERVER") }) return order
            if (order.toSet() != LEGACY_UI_ORDER.toSet() && order.toSet() != LEGACY_FULL_ORDER.toSet()) {
                return order
            }
            val expanded = ArrayList<KeyboxPriorityCategory>(order.size * 2)
            for (category in order) {
                expanded.add(category)
                try {
                    expanded.add(valueOf("${category.name}_SERVER"))
                } catch (_: IllegalArgumentException) {
                    return order
                }
            }
            return expanded
        }

        fun fromValidityAndLevel(
            validityState: KeyboxVerifier.ValidityState,
            invalidReason: KeyboxVerifier.InvalidReason?,
            securityLevel: String,
        ): KeyboxPriorityCategory = fromValidityLevelAndOrigin(validityState, invalidReason, securityLevel, false)

        fun fromValidityLevelAndOrigin(
            validityState: KeyboxVerifier.ValidityState,
            invalidReason: KeyboxVerifier.InvalidReason?,
            securityLevel: String,
            isServer: Boolean,
        ): KeyboxPriorityCategory {
            val levelSuffix = when (securityLevel) {
                "RKP" -> "RKP"
                "StrongBox" -> "STRONGBOX"
                "TEE" -> "TEE"
                else -> "UNKNOWN"
            }
            val base = when {
                validityState == KeyboxVerifier.ValidityState.VALID -> {
                    "VALID_$levelSuffix"
                }
                invalidReason == KeyboxVerifier.InvalidReason.EXPIRED -> {
                    "INVALID_EXPIRED_$levelSuffix"
                }
                invalidReason == KeyboxVerifier.InvalidReason.REVOKED -> {
                    "INVALID_REVOKED_$levelSuffix"
                }
                else -> {
                    "INVALID_VERIFICATION_FAILED_$levelSuffix"
                }
            }
            return if (isServer) valueOf("${base}_SERVER") else valueOf(base)
        }
    }
}

data class KeyboxPriorityPreference(
    val mode: Mode = Mode.DEFAULT,
    val customOrder: List<KeyboxPriorityCategory> = emptyList(),
) {
    enum class Mode {
        DEFAULT,
        CUSTOM,
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode.name.lowercase())
        if (mode == Mode.CUSTOM && customOrder.isNotEmpty()) {
            put("customOrder", JSONArray(customOrder.map { it.name }))
        }
    }

    fun effectiveOrder(): List<KeyboxPriorityCategory> =
        if (mode == Mode.CUSTOM && customOrder.isNotEmpty()) {
            KeyboxPriorityCategory.expandToFullOrder(customOrder)
        } else {
            KeyboxPriorityCategory.DEFAULT_ORDER
        }

    companion object {
        val DEFAULT = KeyboxPriorityPreference()

        fun fromJson(json: JSONObject?): KeyboxPriorityPreference {
            if (json == null) return DEFAULT
            return try {
                val modeStr = json.optString("mode", "default")
                val mode = when (modeStr.lowercase()) {
                    "custom" -> Mode.CUSTOM
                    else -> Mode.DEFAULT
                }
                val orderArray = json.optJSONArray("customOrder")
                val customOrder = if (mode == Mode.CUSTOM) {
                    if (orderArray == null ||
                        (orderArray.length() != KeyboxPriorityCategory.DEFAULT_ORDER.size &&
                            orderArray.length() != KeyboxPriorityCategory.UI_ORDER.size &&
                            orderArray.length() != KeyboxPriorityCategory.LEGACY_FULL_ORDER.size &&
                            orderArray.length() != KeyboxPriorityCategory.LEGACY_UI_ORDER.size)
                    ) {
                        Logger.w("Invalid custom priority order: incomplete; falling back to default")
                        return DEFAULT
                    }
                    val parsed = mutableListOf<KeyboxPriorityCategory>()
                    for (i in 0 until orderArray.length()) {
                        val name = orderArray.optString(i)
                        try {
                            parsed.add(KeyboxPriorityCategory.valueOf(name))
                        } catch (_: IllegalArgumentException) {
                            Logger.w("Invalid custom priority order: unknown category; falling back to default")
                            return DEFAULT
                        }
                    }
                    // Full 32-permutations and UI twelve-permutations pass through;
                    // the UI list expands via effectiveOrder. Legacy six/sixteen
                    // permutations migrate by expanding every entry to its
                    // local/server pair so saved orders keep working.
                    val parsedSet = parsed.toSet()
                    val migrated = KeyboxPriorityCategory.migrateLegacyOrder(parsed)
                    val accepted = when {
                        parsedSet == KeyboxPriorityCategory.DEFAULT_ORDER.toSet() -> parsed.toList()
                        parsedSet == KeyboxPriorityCategory.UI_ORDER.toSet() -> parsed.toList()
                        parsedSet == KeyboxPriorityCategory.LEGACY_FULL_ORDER.toSet() ||
                            parsedSet == KeyboxPriorityCategory.LEGACY_UI_ORDER.toSet() -> migrated
                        else -> {
                            Logger.w("Invalid custom priority order: duplicate or missing category; falling back to default")
                            return DEFAULT
                        }
                    }
                    accepted
                } else {
                    emptyList()
                }
                KeyboxPriorityPreference(mode, customOrder)
            } catch (_: Exception) {
                Logger.w("Failed to parse keybox priority preference; using default")
                DEFAULT
            }
        }
    }
}

object KeyboxPriorityOrder {
    internal fun <T> filterTopPriorityTier(
        candidates: List<T>,
        order: List<KeyboxPriorityCategory>,
        categorySelector: (T) -> KeyboxPriorityCategory,
    ): List<T> {
        if (candidates.size <= 1) return candidates
        val rankMap = order.mapIndexed { index, cat -> cat to index }.toMap()
        var minRank = Int.MAX_VALUE
        val ranked = ArrayList<Pair<T, Int>>(candidates.size)
        for (item in candidates) {
            val cat = categorySelector(item)
            val rank = rankMap[cat] ?: Int.MAX_VALUE
            if (rank < minRank) minRank = rank
            ranked.add(item to rank)
        }
        if (minRank == Int.MAX_VALUE) return candidates
        val topTier = ArrayList<T>()
        for ((item, rank) in ranked) {
            if (rank == minRank) {
                topTier.add(item)
            }
        }
        return if (topTier.isEmpty()) candidates else topTier
    }

    @JvmStatic
    fun filterEligibleCandidates(candidates: List<CertHack.KeyBox>?): List<CertHack.KeyBox> {
        if (candidates.isNullOrEmpty()) return emptyList()
        val blockInvalid = Config.isBlockInvalidKeyboxesEnabled
        return candidates.filter { box ->
            KeyboxValidityTracker.isEligible(box.filename, blockInvalid)
        }
    }

    @JvmStatic
    fun filterTopPriorityTier(candidates: List<CertHack.KeyBox>): List<CertHack.KeyBox> {
        val eligibleCandidates = filterEligibleCandidates(candidates)
        if (eligibleCandidates.size <= 1) return eligibleCandidates
        val preference = Config.keyboxPriorityPreference
        if (preference.mode != KeyboxPriorityPreference.Mode.CUSTOM || preference.customOrder.isEmpty()) {
            return eligibleCandidates
        }
        return filterTopPriorityTier(eligibleCandidates, preference.effectiveOrder()) { box ->
            val entry = KeyboxValidityTracker.getState(box.filename())
            val validity = entry?.validityState ?: KeyboxVerifier.ValidityState.VALID
            val reason = entry?.invalidReason
            // Publish-cached level: no PKIX validation or native inspection per call.
            // Origin is a filename-prefix check only, so the hot path stays free of
            // syscalls and crypto. Remote content always carries the server_ prefix
            // from ServerManager; a local file with that prefix sorts as server tier.
            val level = CertHack.cachedPriorityLevel(box)
            val isServer = KeyboxPriorityCategory.isServerKeybox(box)
            KeyboxPriorityCategory.fromValidityLevelAndOrigin(validity, reason, level, isServer)
        }
    }
}
