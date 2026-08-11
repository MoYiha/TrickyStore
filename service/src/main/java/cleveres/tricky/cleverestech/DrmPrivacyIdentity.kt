package cleveres.tricky.cleverestech

import java.security.MessageDigest

/**
 * Derives a stable pseudonymous DRM identifier from the existing application
 * privacy identity. The genuine DRM device identifier is never used as input.
 */
internal object DrmPrivacyIdentity {
    private const val FIRST_APPLICATION_UID = 10_000
    internal const val MIN_IDENTIFIER_BYTES = 8
    internal const val MAX_IDENTIFIER_BYTES = 64
    private val domain = "CleveresTricky.DrmPrivacy.v1".toByteArray(Charsets.UTF_8)
    private val digest = ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }

    fun idForUid(
        uid: Int,
        length: Int,
    ): ByteArray? {
        if (uid < FIRST_APPLICATION_UID || length !in MIN_IDENTIFIER_BYTES..MAX_IDENTIFIER_BYTES) return null
        if (!Config.isSpoofEnabled || Config.getAppPrivacyMode(uid) != Config.AppPrivacyMode.ISOLATE) return null

        val identity = Config.getTelephonyIdentityOverrides(uid)
        val components =
            listOfNotNull(
                identity.template,
                identity.imei,
                identity.imei2,
                identity.imsi,
                identity.imsi2,
                identity.iccid,
                identity.iccid2,
                identity.meid,
                identity.meid2,
                identity.phoneNumber,
                identity.phoneNumber2,
                identity.serial,
            )
        if (components.isEmpty()) return null
        return derive(uid, length, components)
    }

    @androidx.annotation.VisibleForTesting
    internal fun derive(
        uid: Int,
        length: Int,
        components: List<String>,
    ): ByteArray {
        require(uid >= FIRST_APPLICATION_UID) { "DRM privacy identity requires an application UID" }
        require(length in MIN_IDENTIFIER_BYTES..MAX_IDENTIFIER_BYTES) { "Unsupported DRM identifier length" }
        require(components.isNotEmpty() && components.all { it.isNotEmpty() }) {
            "DRM privacy identity requires non-empty identity components"
        }

        val output = ByteArray(length)
        var offset = 0
        var counter = 0
        while (offset < output.size) {
            val md = requireNotNull(digest.get()) { "SHA-256 digest is unavailable" }
            md.reset()
            md.update(domain)
            md.update(0.toByte())
            updateInt(md, uid)
            updateInt(md, length)
            for (component in components) {
                val encoded = component.toByteArray(Charsets.UTF_8)
                try {
                    updateInt(md, encoded.size)
                    md.update(encoded)
                } finally {
                    encoded.fill(0)
                }
            }
            updateInt(md, counter++)

            val block = md.digest()
            try {
                val count = minOf(block.size, output.size - offset)
                System.arraycopy(block, 0, output, offset, count)
                offset += count
            } finally {
                block.fill(0)
            }
        }
        return output
    }

    private fun updateInt(
        digest: MessageDigest,
        value: Int,
    ) {
        digest.update((value ushr 24).toByte())
        digest.update((value ushr 16).toByte())
        digest.update((value ushr 8).toByte())
        digest.update(value.toByte())
    }
}
