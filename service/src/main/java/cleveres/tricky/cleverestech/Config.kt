package cleveres.tricky.cleverestech

import android.content.pm.IPackageManager
import android.os.FileObserver
import android.os.ServiceManager
import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.DeviceKeyManager
import cleveres.tricky.cleverestech.util.KeyboxAutoCleaner
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import cleveres.tricky.cleverestech.util.PackageTrie
import cleveres.tricky.cleverestech.util.RandomUtils
import cleveres.tricky.cleverestech.util.SecureFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Config {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val runtimeControllerSignal = Semaphore(0)
    private const val MAX_UID_CACHE_ENTRIES = 4096
    private const val UID_DECISION_CACHE_TTL_MS = 5 * 1000L
    private const val FIRST_APPLICATION_UID = 10_000
    private val rkpInfrastructurePackages =
        setOf(
            "com.android.rkpd",
            "com.android.rkpdapp",
            "com.android.remoteprovisioner",
            "com.google.android.rkpd",
            "com.google.android.rkpdapp",
            "com.google.android.go.rkpd",
            "com.google.android.remoteprovisioner",
        )

    private fun <T> putBoundedUidCache(
        cache: ConcurrentHashMap<Int, T>,
        uid: Int,
        value: T,
    ) {
        if (cache.size >= MAX_UID_CACHE_ENTRIES && !cache.containsKey(uid)) cache.clear()
        cache[uid] = value
    }

    enum class AppPrivacyMode(val configValue: String) {
        INHERIT("inherit"),
        REDACT("redact"),
        ISOLATE("isolate"),
        ;

        companion object {
            fun parse(value: String): AppPrivacyMode? = entries.firstOrNull { it.configValue.equals(value, ignoreCase = true) }
        }
    }

    data class AppSpoofConfig(
        val template: String?,
        val keyboxFilename: String?,
        val privacyMode: AppPrivacyMode = AppPrivacyMode.INHERIT,
    )

    internal data class IdentityOverrides(
        val template: String? = null,
        val imei: String? = null,
        val imei2: String? = null,
        val imsi: String? = null,
        val imsi2: String? = null,
        val iccid: String? = null,
        val iccid2: String? = null,
        val meid: String? = null,
        val meid2: String? = null,
        val phoneNumber: String? = null,
        val phoneNumber2: String? = null,
        val serial: String? = null,
        val visibleSimCount: Int? = null,
        val visibleCameraCount: Int? = null,
    ) {
        private fun valueForSlot(
            primary: String?,
            secondary: String?,
            slotIndex: Int,
        ): String? =
            when (slotIndex) {
                0 -> primary
                1 -> secondary ?: primary
                else -> null
            }

        fun imeiForSlot(slotIndex: Int): String? = valueForSlot(imei, imei2, slotIndex)

        fun imsiForSlot(slotIndex: Int): String? = valueForSlot(imsi, imsi2, slotIndex)

        fun iccidForSlot(slotIndex: Int): String? = valueForSlot(iccid, iccid2, slotIndex)

        fun meidForSlot(slotIndex: Int): String? = valueForSlot(meid, meid2, slotIndex)

        fun phoneNumberForSlot(slotIndex: Int): String? = valueForSlot(phoneNumber, phoneNumber2, slotIndex)
    }

    private data class CachedDecision(val value: Boolean, val timestamp: Long)

    private data class CachedValue<T>(val value: T, val timestamp: Long)

    private class TargetState(
        val hackPackages: PackageTrie<Boolean>,
    ) {
        val hackCache = ConcurrentHashMap<Int, CachedDecision>()
    }

    @Volatile
    private var targetState = TargetState(PackageTrie())

    private val rkpInfrastructureCache = ConcurrentHashMap<Int, CachedDecision>()

    @Volatile
    var isGlobalMode = false
        private set

    @Volatile
    var isSpoofEnabled = false
        private set

    @Volatile
    var isBuildIdentityEnabled = false
        private set

    @Volatile
    var isTeeBrokenMode = false
        private set

    @Volatile
    private var moduleHash: ByteArray? = null

    @Volatile
    var isTelephonyEnabled = false

    @Volatile
    var isCameraVisibilityEnabled = false
        private set

    @Volatile
    var isRkpPassthroughEnabled = false
        private set

    @Volatile
    var isDrmPassthroughEnabled = false
        private set

    private class DrmState(
        val packages: PackageTrie<Boolean>,
    ) {
        val cache = ConcurrentHashMap<Int, CachedDecision>()
    }

    @Volatile
    private var drmState = DrmState(PackageTrie())

    @Volatile
    private var moduleHashFromVars: ByteArray? = null

    private class AppConfigState(
        val configs: PackageTrie<AppSpoofConfig>,
        val hasPrivacyRules: Boolean = false,
    ) {
        val cache = ConcurrentHashMap<Int, CachedValue<AppSpoofConfig?>>()
        val privacyCache = ConcurrentHashMap<Int, CachedValue<AppPrivacyMode>>()
        val identityCache = ConcurrentHashMap<Int, CachedValue<IdentityOverrides>>()
    }

    @Volatile
    private var appConfigState = AppConfigState(PackageTrie())

    fun getModuleHash(): ByteArray? = moduleHash ?: moduleHashFromVars

    fun getAppConfig(uid: Int): AppSpoofConfig? {
        val state = appConfigState
        if (state.configs.isEmpty()) {
            cacheValue(state.cache, uid, null)
            return PolicyState.resolveAppConfig(uid, null)
        }
        val pkgs = getPackages(uid)
        getCachedValue(state.cache, uid)?.let { return PolicyState.resolveAppConfig(uid, it.value) }
        var result: AppSpoofConfig? = null
        val len = pkgs.size
        for (i in 0 until len) {
            val config = state.configs.get(pkgs[i])
            if (config != null) {
                result = config
                break
            }
        }
        cacheValue(state.cache, uid, result)
        return PolicyState.resolveAppConfig(uid, result)
    }

    fun getAppPrivacyMode(uid: Int): AppPrivacyMode {
        PolicyState.profilePrivacyMode(uid)?.let { return it }
        val state = appConfigState
        if (!state.hasPrivacyRules) {
            cacheValue(state.privacyCache, uid, AppPrivacyMode.INHERIT)
            return AppPrivacyMode.INHERIT
        }
        val packages = getPackages(uid)
        getCachedValue(state.privacyCache, uid)?.let { return it.value }
        var selected = AppPrivacyMode.INHERIT
        for (packageName in packages) {
            when (state.configs.get(packageName)?.privacyMode) {
                AppPrivacyMode.REDACT -> {
                    selected = AppPrivacyMode.REDACT
                    break
                }
                AppPrivacyMode.ISOLATE -> selected = AppPrivacyMode.ISOLATE
                else -> Unit
            }
        }
        cacheValue(state.privacyCache, uid, selected)
        return selected
    }

    val shouldInterceptTelephony: Boolean
        get() =
            PolicyState.isFeatureEnabled(PolicyState.Feature.TELEPHONY_IDENTITY) ||
                (isSpoofEnabled && appConfigState.hasPrivacyRules) ||
                PolicyState.hasTelephonyProfileWork()

    val shouldInterceptDrm: Boolean
        get() = (isSpoofEnabled && appConfigState.hasPrivacyRules) || PolicyState.hasDrmProfileWork()

    val shouldInterceptSubscriptionVisibility: Boolean
        get() = identityOverrides.visibleSimCount != null && shouldInterceptTelephony

    fun getVisibleSimCount(uid: Int): Int? =
        identityOverrides.visibleSimCount.takeIf { shouldApplyTelephonyPrivacy(uid) }

    val shouldInterceptCameraVisibility: Boolean
        get() = shouldRunCameraVisibility(isCameraVisibilityEnabled, identityOverrides.visibleCameraCount)

    fun getVisibleCameraCount(uid: Int): Int? =
        identityOverrides.visibleCameraCount.takeIf { isCameraVisibilityEnabled && isTargetedUid(uid) }

    fun shouldApplyTelephonyPrivacy(uid: Int): Boolean {
        val legacyPrivacy = !PolicyState.usesV2() && isSpoofEnabled && getAppPrivacyMode(uid) != AppPrivacyMode.INHERIT
        val configuredPrivacy = PolicyState.usesV2() && getAppPrivacyMode(uid) != AppPrivacyMode.INHERIT
        return (PolicyState.isFeatureEnabled(PolicyState.Feature.TELEPHONY_IDENTITY, uid) || legacyPrivacy || configuredPrivacy) &&
            isTargetedUid(uid)
    }

    internal fun updateAppConfigs(f: File?) =
        runCatching {
            val newConfigs = PackageTrie<AppSpoofConfig>()
            val seenPackages = HashSet<String>()
            var hasPrivacyRules = false
            if (f != null && Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    "app_config must be a regular file"
                }
                require(f.length() in 0..MAX_APP_CONFIG_BYTES) {
                    "app_config has an invalid size"
                }
                var ruleCount = 0
                f.useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank() && !line.startsWith("#")) {
                            require(++ruleCount <= MAX_APP_CONFIG_RULES) {
                                "app_config contains too many rules"
                            }
                            val trimmed = line.trim()
                            if (trimmed.isEmpty()) return@forEach
                            val len = trimmed.length
                            var idx = 0
                            var start = idx
                            while (idx < len && !trimmed[idx].isWhitespace()) idx++
                            val pkg = trimmed.substring(start, idx)
                            var template: String? = null
                            var keybox: String? = null
                            var privacyMode = AppPrivacyMode.INHERIT
                            while (idx < len && trimmed[idx].isWhitespace()) idx++
                            if (idx < len) {
                                start = idx
                                while (idx < len && !trimmed[idx].isWhitespace()) idx++
                                val tStr = trimmed.substring(start, idx)
                                if (tStr != "null") template = tStr.lowercase()
                                while (idx < len && trimmed[idx].isWhitespace()) idx++
                                if (idx < len) {
                                    start = idx
                                    while (idx < len && !trimmed[idx].isWhitespace()) idx++
                                    val kStr = trimmed.substring(start, idx)
                                    if (kStr != "null") keybox = kStr
                                    while (idx < len && trimmed[idx].isWhitespace()) idx++
                                    if (idx < len) {
                                        start = idx
                                        while (idx < len && !trimmed[idx].isWhitespace()) idx++
                                        privacyMode =
                                            AppPrivacyMode.parse(trimmed.substring(start, idx))
                                                ?: throw IllegalArgumentException("Invalid app privacy mode")
                                    }
                                }
                            }
                            while (idx < len && trimmed[idx].isWhitespace()) idx++
                            require(idx == len) { "app_config contains too many columns" }
                            require(APP_PACKAGE_PATTERN.matches(pkg)) { "app_config contains an invalid package" }
                            require(seenPackages.add(pkg)) { "app_config contains duplicate packages" }
                            require(template == null || validTemplateName.matches(template)) {
                                "app_config contains an invalid template"
                            }
                            require(keybox == null || isValidAppKeybox(keybox)) {
                                "app_config contains an invalid keybox"
                            }
                            require(template != null || keybox != null || privacyMode != AppPrivacyMode.INHERIT) {
                                "app_config contains an empty rule"
                            }
                            if (privacyMode != AppPrivacyMode.INHERIT) hasPrivacyRules = true
                            newConfigs.add(pkg, AppSpoofConfig(template, keybox, privacyMode))
                        }
                    }
                }
            }
            appConfigState = AppConfigState(newConfigs, hasPrivacyRules)
            CertHack.clearCertificateCache()
            signalRuntimeController()
            Logger.i { "update app configs: ${newConfigs.size}" }
        }.onFailure {
            Logger.e("failed to update app configs", it)
        }

    @androidx.annotation.VisibleForTesting
    internal fun setPackagesForTesting(
        uid: Int,
        packages: Array<String>,
    ) {
        putBoundedUidCache(packageCache, uid, CachedPackage(packages.clone(), System.currentTimeMillis()))
        PolicyState.invalidateUid(uid)
    }

    fun parsePackages(lines: Sequence<String>): PackageTrie<Boolean> = parsePackages(lines, Int.MAX_VALUE)

    private fun parsePackages(
        lines: Sequence<String>,
        maxRules: Int,
    ): PackageTrie<Boolean> {
        val hackPackages = PackageTrie<Boolean>()
        var ruleCount = 0
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            require(++ruleCount <= maxRules) { "target.txt contains too many rules" }
            val packageName = trimmed.removeSuffix("!").trim()
            val valid =
                packageName.isNotEmpty() &&
                    packageName.all { character ->
                        character.isLetterOrDigit() || character == '_' || character == '.' || character == '*'
                    }
            if (valid) {
                hackPackages.add(packageName, true)
            } else {
                Logger.w("Ignoring invalid target package entry")
            }
        }
        return hackPackages
    }

    private fun updateTargetPackages(f: File?) =
        runCatching {
            if (isGlobalMode) {
                targetState = TargetState(PackageTrie())
                Logger.i("Global mode is enabled, skipping updateTargetPackages execution.")
                return@runCatching
            }
            Logger.d("updateTargetPackages: reading ${f?.absolutePath} (exists=${f?.exists()})")
            val packages =
                if (f != null && Files.exists(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    require(Files.isRegularFile(f.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        "target.txt must be a regular file"
                    }
                    require(f.length() in 0..MAX_TARGET_FILE_BYTES) {
                        "target.txt has an invalid size"
                    }
                    f.useLines { lines -> parsePackages(lines, MAX_TARGET_PACKAGE_RULES) }
                } else {
                    Logger.d("updateTargetPackages: target file missing or null, using empty package list")
                    parsePackages(emptySequence())
                }
            targetState = TargetState(packages)
            Logger.i { "Updated target packages: ${packages.size}" }
        }.onFailure {
            Logger.e("failed to update target files", it)
        }

    @Volatile
    private var cachedLegacyKeyboxes: List<CertHack.KeyBox> = emptyList()

    @Volatile
    private var lastKeyboxModified: Long = 0

    @Volatile
    private var lastKeyboxLength: Long = 0

    private data class KeyboxFileCache(
        val lastModified: Long,
        val length: Long,
        val keyboxes: List<CertHack.KeyBox>,
    )

    private val directoryKeyboxCache = ConcurrentHashMap<String, KeyboxFileCache>()
    private const val MAX_KEYBOX_XML_BYTES = 10L * 1024 * 1024
    private const val MAX_KEYBOX_FILES = 64

    fun updateKeyBoxes() =
        scope.launch {
            updateKeyBoxesSync()
        }

    fun updateKeyBoxesSync(): Boolean =
        updateKeyBoxesSyncWith(
            revocationProvider = { KeyboxVerifier.fetchCrl() },
            verifier = { keybox, crl -> KeyboxVerifier.verifyKeybox(keybox, crl) },
        )

    fun updateKeyBoxesSync(revokedSerials: Set<String>?): Boolean =
        updateKeyBoxesSyncWith(
            revocationProvider = {
                revokedSerials?.let { serials -> CrlWire.Handle.fromLegacySerials(serials) }
            },
            verifier = { keybox, crl -> KeyboxVerifier.verifyKeybox(keybox, crl) },
        )

    internal fun updateKeyBoxesSyncWith(
        revocationProvider: () -> CrlWire.Handle?,
        verifier: (CertHack.KeyBox, CrlWire.Handle) -> KeyboxVerifier.Status,
    ): Boolean {
        val candidates = ArrayList<CertHack.KeyBox>()
        val crl = revocationProvider()
        if (crl == null) {
            Logger.e("CRL unavailable; refusing to change active keybox state")
            KeyboxActivation.recordFailure()
            return false
        }

        if (keybox.exists()) {
            try {
                val modified = keybox.lastModified()
                val length = keybox.length()
                if (length !in 1..MAX_KEYBOX_XML_BYTES) {
                    throw IOException("keybox.xml has an invalid size")
                }
                if (modified != lastKeyboxModified || length != lastKeyboxLength || cachedLegacyKeyboxes.isEmpty()) {
                    val parsed = KeyboxLoader.parseKeyboxFile(KeyboxLoader.Scope.CONFIG_ROOT, keybox.name)
                    cachedLegacyKeyboxes = KeyboxJcaAdapter.materialize(parsed, keybox.name)
                    lastKeyboxModified = modified
                    lastKeyboxLength = length
                }
                candidates.addAll(cachedLegacyKeyboxes)
            } catch (error: RustBackendUnavailableException) {
                Logger.e("Rust backend unavailable while loading keybox.xml", error)
                KeyboxActivation.recordFailure()
                return false
            } catch (error: Exception) {
                Logger.e("Failed to parse keybox.xml", error)
            }
        } else {
            cachedLegacyKeyboxes = emptyList()
            lastKeyboxModified = 0
            lastKeyboxLength = 0
        }

        val files =
            keyboxDir.listFiles { file ->
                file.isFile && file.name.endsWith(".xml", ignoreCase = true)
            }?.sortedBy { it.name }
                ?: emptyList()
        if (files.size > MAX_KEYBOX_FILES) {
            Logger.e("Too many keybox XML files; maximum is $MAX_KEYBOX_FILES")
            KeyboxActivation.recordFailure()
            return false
        }
        val activeNames = HashSet<String>()
        for (file in files) {
            activeNames += file.name
            try {
                if (file.length() !in 1..MAX_KEYBOX_XML_BYTES) {
                    Logger.e("Skipping oversized keybox file: ${file.name}")
                    directoryKeyboxCache.remove(file.name)
                    continue
                }
                val modified = file.lastModified()
                val length = file.length()
                val cached = directoryKeyboxCache[file.name]
                if (cached != null && cached.lastModified == modified && cached.length == length) {
                    candidates.addAll(cached.keyboxes)
                    continue
                }
                val parsed = KeyboxLoader.parseKeyboxFile(KeyboxLoader.Scope.KEYBOX_DIRECTORY, file.name)
                val materialized = KeyboxJcaAdapter.materialize(parsed, file.name)
                directoryKeyboxCache[file.name] = KeyboxFileCache(modified, length, materialized)
                candidates.addAll(materialized)
            } catch (error: RustBackendUnavailableException) {
                Logger.e("Rust backend unavailable while loading keybox directory", error)
                KeyboxActivation.recordFailure()
                return false
            } catch (error: Exception) {
                Logger.e("Skipping invalid keybox file: ${file.name}", error)
                directoryKeyboxCache.remove(file.name)
            }
        }
        directoryKeyboxCache.keys.removeIf { it !in activeNames }

        candidates.addAll(CboxManager.getUnlockedKeyboxes())

        val verified = ArrayList<CertHack.KeyBox>(candidates.size)
        for (candidate in candidates) {
            when (val status = verifier(candidate, crl)) {
                KeyboxVerifier.Status.VALID -> verified += candidate
                else -> Logger.w("Keybox ${candidate.name} rejected during activation: $status")
            }
        }
        return KeyboxActivation.commitAndPublish(verified)
    }

    private fun updateKeyBoxesWithPublicKey(publicKey: String?): Boolean {
        val crl = KeyboxVerifier.fetchCrl()
        if (crl == null) {
            Logger.e("CRL unavailable; refusing to change active keybox state")
            KeyboxActivation.recordFailure()
            return false
        }
        val encrypted = cboxManager?.openCboxWithPublicKey(publicKey) ?: return false
        return try {
            val keyboxes = KeyboxJcaAdapter.materialize(encrypted.document, encrypted.sourceName)
            val verified = keyboxes.filter { KeyboxVerifier.verifyKeybox(it, crl) == KeyboxVerifier.Status.VALID }
            if (verified.size != keyboxes.size) {
                Logger.e("CBOX keybox rejected during activation")
                KeyboxActivation.recordFailure()
                false
            } else {
                KeyboxActivation.commitAndPublish(verified)
            }
        } finally {
            encrypted.wipe()
        }
    }

    fun isKeyboxUnlocked(): Boolean = CertHack.getKeyboxCount() > 0

    fun getKeyboxStatus(): String {
        val count = CertHack.getKeyboxCount()
        return when {
            count > 0 -> "Active ($count)"
            KeyboxActivation.isHealthy() -> "No verified keybox active"
            else -> "Activation unavailable; previous active keyboxes preserved"
        }
    }

    fun isKeyboxActive(): Boolean = CertHack.getKeyboxCount() > 0

    fun isKeyboxHealthy(): Boolean = KeyboxActivation.isHealthy()

    fun getActiveKeyboxCount(): Int = CertHack.getKeyboxCount()

    fun getKeyboxActivationStatus(): String = KeyboxActivation.statusMessage()

    fun getKeyboxFiles(): List<String> =
        keyboxDir.listFiles { file -> file.isFile && (file.name.endsWith(".xml", true) || file.name.endsWith(".cbox", true)) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    fun getLockedKeyboxFiles(): Set<String> = CboxManager.getLockedFiles()

    fun getKeyboxDirectory(): File = keyboxDir

    fun getKeyboxFile(name: String): File? {
        if (!KEYBOX_FILENAME_PATTERN.matches(name)) return null
        return File(keyboxDir, name).takeIf { it.isFile }
    }

    fun getLegacyKeyboxFile(): File = keybox

    fun replaceLegacyKeybox(bytes: ByteArray): Boolean =
        runCatching {
            if (bytes.isEmpty() || bytes.size > MAX_KEYBOX_XML_BYTES) return@runCatching false
            SecureFile.writeBytes(keybox, bytes)
            true
        }.getOrDefault(false)

    fun writeKeyboxFile(
        name: String,
        bytes: ByteArray,
    ): Boolean =
        runCatching {
            if (!KEYBOX_FILENAME_PATTERN.matches(name) || bytes.isEmpty() || bytes.size > MAX_KEYBOX_XML_BYTES) {
                return@runCatching false
            }
            SecureFile.writeBytes(File(keyboxDir, name), bytes)
            true
        }.getOrDefault(false)

    fun deleteKeyboxFile(name: String): Boolean {
        if (!KEYBOX_FILENAME_PATTERN.matches(name)) return false
        val file = File(keyboxDir, name)
        if (!file.exists()) return true
        return !Files.isSymbolicLink(file.toPath()) && file.delete()
    }

    fun deleteLegacyKeybox(): Boolean {
        if (!keybox.exists()) return true
        return !Files.isSymbolicLink(keybox.toPath()) && keybox.delete()
    }

    fun hasLegacyKeybox(): Boolean = keybox.exists()

    fun getKeyboxContent(): ByteArray? {
        if (!keybox.isFile || keybox.length() !in 1..MAX_KEYBOX_XML_BYTES) return null
        return keybox.readBytes()
    }

    fun replaceCbox(
        name: String,
        bytes: ByteArray,
    ): Boolean =
        runCatching {
            if (!CBOX_FILENAME_PATTERN.matches(name) || bytes.isEmpty() || bytes.size > MAX_CBOX_BYTES) return@runCatching false
            SecureFile.writeBytes(File(keyboxDir, name), bytes)
            CboxManager.refresh()
            true
        }.getOrDefault(false)

    fun deleteCbox(name: String): Boolean {
        if (!CBOX_FILENAME_PATTERN.matches(name)) return false
        val file = File(keyboxDir, name)
        if (!file.exists()) return true
        val result = !Files.isSymbolicLink(file.toPath()) && file.delete()
        if (result) CboxManager.refresh()
        return result
    }

    fun unlockCbox(
        name: String,
        password: String,
        publicKey: String?,
    ): Boolean = CboxManager.unlock(name, password, publicKey)

    @androidx.annotation.VisibleForTesting
    internal fun updateKeyBoxesWithPublicKeyForTesting(publicKey: String?): Boolean = updateKeyBoxesWithPublicKey(publicKey)

    private fun updateSecurityPatch(f: File?) {
        val default = PatchTargets.defaults()
        val state = PatchTargets.fromFile(f, default) ?: default
        if (state != default) Logger.i("Security patch policy override is active")
        val resolved = state.resolve(default)
        systemPatchLevel = resolved.systemPatchLevel
        vendorPatchLevel = resolved.vendorPatchLevel
        bootPatchLevel = resolved.bootPatchLevel
    }

    private fun updateBuildVars(f: File?) {
        val buildVars = LinkedHashMap<String, String>()
        if (f != null && f.isFile) {
            f.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val index = trimmed.indexOf('=')
                if (index <= 0) return@forEachLine
                val key = trimmed.substring(0, index).trim()
                val value = trimmed.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) buildVars[key] = value
            }
        }
        buildVarMap = buildVars
    }

    private fun updateIdentityOverrides(f: File?) {
        val values = LinkedHashMap<String, String>()
        if (f != null && f.isFile) {
            f.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val index = trimmed.indexOf('=')
                if (index <= 0) return@forEachLine
                val key = trimmed.substring(0, index).trim()
                val value = trimmed.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) values[key] = value
            }
        }
        identityOverrides =
            IdentityOverrides(
                template = values["template"]?.takeIf { validTemplateName.matches(it) },
                imei = values["imei"]?.takeIf(::isValidImei),
                imei2 = values["imei2"]?.takeIf(::isValidImei),
                imsi = values["imsi"]?.takeIf(::isValidImsi),
                imsi2 = values["imsi2"]?.takeIf(::isValidImsi),
                iccid = values["iccid"]?.takeIf(::isValidIccid),
                iccid2 = values["iccid2"]?.takeIf(::isValidIccid),
                meid = values["meid"]?.takeIf(::isValidMeid),
                meid2 = values["meid2"]?.takeIf(::isValidMeid),
                phoneNumber = values["phone_number"]?.takeIf(::isValidPhoneNumber),
                phoneNumber2 = values["phone_number2"]?.takeIf(::isValidPhoneNumber),
                serial = values["serial"]?.takeIf { it.length <= MAX_SERIAL_LENGTH },
                visibleSimCount = values["visible_sim_count"]?.toIntOrNull()?.takeIf { it in 0..8 },
                visibleCameraCount = values["visible_camera_count"]?.toIntOrNull()?.takeIf { it in 0..16 },
            )
    }

    private fun updateModuleHash(f: File?) {
        moduleHashFromVars =
            if (f != null && f.isFile) {
                runCatching {
                    val text = f.readText().trim()
                    if (text.length != 64 || text.any { it.digitToIntOrNull(16) == null }) return@runCatching null
                    text.hexToByteArray()
                }.getOrNull()
            } else {
                null
            }
    }

    fun applyProfile(profile: String) {
        require(profile in setOf("maximum", "daily", "minimal", "default")) { "Unknown profile" }
        Logger.i("Applying profile: $profile")
        removeConfigFiles(CAMERA_VISIBILITY_FILE)
        when (profile) {
            "maximum" -> {
                SecureFile.touch(File(root, SPOOF_ENABLED_FILE), 384)
                SecureFile.touch(File(root, BUILD_IDENTITY_FILE), 384)
                SecureFile.touch(File(root, GLOBAL_MODE_FILE), 384)
                removeConfigFiles(TEE_BROKEN_MODE_FILE, BootLogic.FILE_HIDE_PROPS, BootLogic.FILE_SPOOF_CN, DRM_PASSTHROUGH_FILE)
                SecureFile.touch(File(root, RANDOM_ON_BOOT_FILE), 384)
                SecureFile.touch(File(root, SPOOF_BUILD_VARS_FILE), 384)
                SecureFile.touch(File(root, AUTO_KEYBOX_CHECK_FILE), 384)
                SecureFile.touch(File(root, TELEPHONY_FILE), 384)
            }
            "daily" -> {
                SecureFile.touch(File(root, SPOOF_ENABLED_FILE), 384)
                removeConfigFiles(GLOBAL_MODE_FILE, TEE_BROKEN_MODE_FILE, RANDOM_ON_BOOT_FILE, BootLogic.FILE_HIDE_PROPS,
                    BootLogic.FILE_SPOOF_CN, TELEPHONY_FILE, BUILD_IDENTITY_FILE)
                SecureFile.touch(File(root, SPOOF_BUILD_VARS_FILE), 384)
                SecureFile.touch(File(root, AUTO_KEYBOX_CHECK_FILE), 384)
                SecureFile.touch(File(root, DRM_PASSTHROUGH_FILE), 384)
            }
            "minimal" -> {
                removeConfigFiles(SPOOF_ENABLED_FILE, BUILD_IDENTITY_FILE, GLOBAL_MODE_FILE, TEE_BROKEN_MODE_FILE,
                    RANDOM_ON_BOOT_FILE, BootLogic.FILE_HIDE_PROPS, BootLogic.FILE_SPOOF_CN, AUTO_KEYBOX_CHECK_FILE,
                    TELEPHONY_FILE)
                SecureFile.touch(File(root, DRM_PASSTHROUGH_FILE), 384)
            }
            "default" -> {
                SecureFile.touch(File(root, GLOBAL_MODE_FILE), 384)
                SecureFile.touch(File(root, AUTO_KEYBOX_CHECK_FILE), 384)
                removeConfigFiles(SPOOF_ENABLED_FILE, BUILD_IDENTITY_FILE, TEE_BROKEN_MODE_FILE, RANDOM_ON_BOOT_FILE,
                    BootLogic.FILE_HIDE_PROPS, BootLogic.FILE_SPOOF_CN, TELEPHONY_FILE, RKP_PASSTHROUGH_FILE, DRM_PASSTHROUGH_FILE)
            }
        }
        updateSpoofEnabled(File(root, SPOOF_ENABLED_FILE))
        updateGlobalMode(File(root, GLOBAL_MODE_FILE))
        updateTeeBrokenMode(File(root, TEE_BROKEN_MODE_FILE))
        updateAutoKeyboxCheck(File(root, AUTO_KEYBOX_CHECK_FILE))
        updateTelephonyEnabled(File(root, TELEPHONY_FILE))
        updateBuildIdentityEnabled(File(root, BUILD_IDENTITY_FILE))
        updateDrmPassthrough(File(root, DRM_PASSTHROUGH_FILE))
        runtimeControllerSignal.release()
        PolicyState.reloadNow()
        CertHack.clearCertificateCache()
    }

    private fun updateAutoKeyboxCheck(file: File?) {
        autoKeyboxCheck = file?.exists() == true
    }

    private fun updateSpoofEnabled(file: File?) {
        isSpoofEnabled = file?.exists() == true
    }

    private fun updateGlobalMode(file: File?) {
        isGlobalMode = file?.exists() == true
    }

    private fun updateTeeBrokenMode(file: File?) {
        isTeeBrokenMode = file?.exists() == true
    }

    private fun updateTelephonyEnabled(file: File?) {
        isTelephonyEnabled = file?.exists() == true
    }

    private fun updateBuildIdentityEnabled(file: File?) {
        isBuildIdentityEnabled = file?.exists() == true
    }

    private fun updateDrmPassthrough(file: File?) {
        isDrmPassthroughEnabled = file?.exists() == true
    }

    private fun updateCameraVisibility(file: File?) {
        isCameraVisibilityEnabled = file?.exists() == true
    }

    private fun updateIdentityOverrides(file: File?) {
        val values = LinkedHashMap<String, String>()
        if (file != null && file.isFile) {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val index = trimmed.indexOf('=')
                if (index <= 0) return@forEachLine
                val key = trimmed.substring(0, index).trim()
                val value = trimmed.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) values[key] = value
            }
        }
        identityOverrides =
            IdentityOverrides(
                template = values["template"]?.takeIf { validTemplateName.matches(it) },
                imei = values["imei"]?.takeIf(::isValidImei),
                imei2 = values["imei2"]?.takeIf(::isValidImei),
                imsi = values["imsi"]?.takeIf(::isValidImsi),
                imsi2 = values["imsi2"]?.takeIf(::isValidImsi),
                iccid = values["iccid"]?.takeIf(::isValidIccid),
                iccid2 = values["iccid2"]?.takeIf(::isValidIccid),
                meid = values["meid"]?.takeIf(::isValidMeid),
                meid2 = values["meid2"]?.takeIf(::isValidMeid),
                phoneNumber = values["phone_number"]?.takeIf(::isValidPhoneNumber),
                phoneNumber2 = values["phone_number2"]?.takeIf(::isValidPhoneNumber),
                serial = values["serial"]?.takeIf { it.length <= MAX_SERIAL_LENGTH },
                visibleSimCount = values["visible_sim_count"]?.toIntOrNull()?.takeIf { it in 0..8 },
                visibleCameraCount = values["visible_camera_count"]?.toIntOrNull()?.takeIf { it in 0..16 },
            )
    }

    private fun updateBuildVars(f: File?) {
        val buildVars = LinkedHashMap<String, String>()
        if (f != null && f.isFile) {
            f.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                val index = trimmed.indexOf('=')
                if (index <= 0) return@forEachLine
                val key = trimmed.substring(0, index).trim()
                val value = trimmed.substring(index + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) buildVars[key] = value
            }
        }
        buildVarMap = buildVars
    }

    private fun updateModuleHash(f: File?) {
        moduleHashFromVars =
            if (f != null && f.isFile) {
                runCatching {
                    val text = f.readText().trim()
                    if (text.length != 64 || text.any { it.digitToIntOrNull(16) == null }) return@runCatching null
                    text.hexToByteArray()
                }.getOrNull()
            } else {
                null
            }
    }

    fun initialize() {
        Logger.i("Config.initialize: starting (root=${root.absolutePath})")
        SecureFile.mkdirs(root, 448)
        SecureFile.mkdirs(keyboxDir, 448)
        KeyboxVerifier.configureCacheRoot(root)
        DeviceKeyManager.initialize(root)
        CboxManager.initialize()
        ServerManager.initialize()
        DeviceTemplateManager.initialize(root)
        PrivacySeedManager.initialize(root)
        PolicyState.initialize(root)
        updateGlobalMode(File(root, GLOBAL_MODE_FILE))
        updateSpoofEnabled(File(root, SPOOF_ENABLED_FILE))
        updateBuildIdentityEnabled(File(root, BUILD_IDENTITY_FILE))
        updateTeeBrokenMode(File(root, TEE_BROKEN_MODE_FILE))
        updateAutoKeyboxCheck(File(root, AUTO_KEYBOX_CHECK_FILE))
        updateTelephonyEnabled(File(root, TELEPHONY_FILE))
        updateDrmPassthrough(File(root, DRM_PASSTHROUGH_FILE))
        updateCameraVisibility(File(root, CAMERA_VISIBILITY_FILE))
        updateIdentityOverrides(File(root, IDENTITY_OVERRIDES_FILE))
        updateSecurityPatch(File(root, SECURITY_PATCH_FILE))
        updateBuildVars(File(root, SPOOF_BUILD_VARS_FILE))
        updateModuleHash(File(root, MODULE_HASH_FILE))
        updateTargetPackages(File(root, TARGET_FILE))
        updateAppConfigs(File(root, APP_CONFIG_FILE))
        updateKeyBoxes()
        installObservers()
        Logger.i("Config.initialize: complete")
    }

    private fun getPackages(uid: Int): Array<String> {
        val cached = packageCache[uid]
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.timestamp < PACKAGE_CACHE_TTL_MS) return cached.packages
        val packages = packageManager.getPackagesForUid(uid) ?: emptyArray()
        cacheValue(packageCache, uid, CachedPackage(packages, now))
        return packages
    }

    private data class CachedPackage(val packages: Array<String>, val timestamp: Long)

    private fun <T> cacheValue(cache: ConcurrentHashMap<Int, CachedValue<T>>, uid: Int, value: T) {
        putBoundedUidCache(cache, uid, CachedValue(value, System.currentTimeMillis()))
    }

    private fun <T> getCachedValue(cache: ConcurrentHashMap<Int, CachedValue<T>>, uid: Int): CachedValue<T>? {
        val cached = cache[uid] ?: return null
        return cached.takeIf { System.currentTimeMillis() - it.timestamp < UID_DECISION_CACHE_TTL_MS }
    }

    private fun signalRuntimeController() {
        runtimeControllerSignal.release()
    }

    internal fun awaitRuntimeControllerSignal(timeoutMs: Long): Boolean =
        runtimeControllerSignal.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)

    private fun removeConfigFiles(vararg names: String) {
        names.forEach { name ->
            val file = File(root, name)
            try {
                if (Files.isSymbolicLink(file.toPath())) {
                    Logger.w("Refusing to remove symbolic config entry: $name")
                } else {
                    Files.deleteIfExists(file.toPath())
                }
            } catch (error: Exception) {
                Logger.e("Failed to remove config file $name", error)
            }
        }
    }

    private fun updatePrivacySeed(f: File?) {
        PrivacySeedManager.initialize(root)
    }

    private object PrivacySeedManager {
        private const val PRIVACY_SEED_FILE = "privacy_seed"
        private const val PRIVACY_SEED_BYTES = 32
        private const val PRIVACY_SEED_HEX_CHARS = PRIVACY_SEED_BYTES * 2
        private val lock = Any()

        @Volatile
        private var currentSeed: ByteArray? = null

        fun initialize(root: File) {
            synchronized(lock) {
                if (currentSeed != null) return
                val file = File(root, PRIVACY_SEED_FILE)
                val loaded = readPrivacySeed(file)
                currentSeed = loaded ?: generatePrivacySeed().also { writePrivacySeed(file, it) }
            }
        }

        fun get(): ByteArray = synchronized(lock) { requireNotNull(currentSeed).clone() }

        private fun readPrivacySeed(file: File): ByteArray? {
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return null
            if (file.length() !in 64L..65L) return null
            val encoded = file.readText().trim()
            if (encoded.length != PRIVACY_SEED_HEX_CHARS || encoded.any { it.digitToIntOrNull(16) == null }) return null
            val decoded = runCatching { encoded.hexToByteArray() }.getOrNull() ?: return null
            return decoded.takeIf { it.size == PRIVACY_SEED_BYTES && it.any { byte -> byte != 0.toByte() } }
        }

        private fun generatePrivacySeed(): ByteArray = ByteArray(PRIVACY_SEED_BYTES).also { SecureRandom().nextBytes(it) }

        private fun writePrivacySeed(file: File, seed: ByteArray) {
            val encoded = encodePrivacySeed(seed)
            try {
                SecureFile.writeBytes(file, encoded)
            } finally {
                encoded.fill(0)
            }
        }

        private fun encodePrivacySeed(seed: ByteArray): ByteArray {
            require(seed.size == PRIVACY_SEED_BYTES && seed.any { it != 0.toByte() })
            val output = ByteArray(PRIVACY_SEED_HEX_CHARS)
            for (index in seed.indices) {
                val value = seed[index].toInt() and 0xff
                output[index * 2] = HEX[value ushr 4]
                output[index * 2 + 1] = HEX[value and 0x0f]
            }
            return output
        }

        private val HEX = "0123456789abcdef".encodeToByteArray()
    }

    private const val PACKAGE_CACHE_TTL_MS = 5_000L
    private const val MAX_APP_CONFIG_BYTES = 1024L * 1024
    private const val MAX_APP_CONFIG_RULES = 2048
    private const val MAX_TARGET_FILE_BYTES = 1024L * 1024
    private const val MAX_TARGET_PACKAGE_RULES = 4096
    private const val MAX_SERIAL_LENGTH = 64
    private val APP_PACKAGE_PATTERN = Regex("[A-Za-z0-9_.]+")
    private val KEYBOX_FILENAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,122}\\.(xml|cbox)", RegexOption.IGNORE_CASE)
    private val CBOX_FILENAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,122}\\.cbox", RegexOption.IGNORE_CASE)
    private val validTemplateName = Regex("[a-z0-9][a-z0-9_.-]{0,63}")
}