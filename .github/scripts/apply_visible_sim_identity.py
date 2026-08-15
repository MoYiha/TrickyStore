from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one exact anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Config: persist a bounded visible SIM count without enabling runtime work.
# ---------------------------------------------------------------------------
rel = "service/src/main/java/cleveres/tricky/cleverestech/Config.kt"
text = read(rel)
text = replace_once(
    text,
    '''        val phoneNumber2: String? = null,\n        val serial: String? = null,\n    ) {''',
    '''        val phoneNumber2: String? = null,\n        val serial: String? = null,\n        val visibleSimCount: Int? = null,\n    ) {''',
    "IdentityOverrides visible SIM field",
)
text = replace_once(
    text,
    '''    val shouldInterceptDrm: Boolean\n        get() = (isSpoofEnabled && appConfigState.hasPrivacyRules) || PolicyState.hasDrmProfileWork()\n\n    fun shouldApplyTelephonyPrivacy(uid: Int): Boolean {''',
    '''    val shouldInterceptDrm: Boolean\n        get() = (isSpoofEnabled && appConfigState.hasPrivacyRules) || PolicyState.hasDrmProfileWork()\n\n    /** Subscription visibility reuses the opt-in telephony runtime and never starts it by itself. */\n    val shouldInterceptSubscriptionVisibility: Boolean\n        get() = identityOverrides.visibleSimCount != null && shouldInterceptTelephony\n\n    fun getVisibleSimCount(uid: Int): Int? =\n        identityOverrides.visibleSimCount.takeIf { shouldApplyTelephonyPrivacy(uid) }\n\n    fun shouldApplyTelephonyPrivacy(uid: Int): Boolean {''',
    "Config subscription visibility gate",
)
text = replace_once(
    text,
    '''                "ATTESTATION_ID_PHONE_NUMBER",\n                "ATTESTATION_ID_PHONE_NUMBER2",\n            )''',
    '''                "ATTESTATION_ID_PHONE_NUMBER",\n                "ATTESTATION_ID_PHONE_NUMBER2",\n                "VISIBLE_SIM_COUNT",\n            )''',
    "supported visible SIM build var",
)
text = replace_once(
    text,
    '''        if (key == "MODULE_HASH") return value.length == 64 && value.all { it.digitToIntOrNull(16) != null }\n        when (key) {''',
    '''        if (key == "MODULE_HASH") return value.length == 64 && value.all { it.digitToIntOrNull(16) != null }\n        if (key == "VISIBLE_SIM_COUNT") return value.length == 1 && value[0] in '0'..'8'\n        when (key) {''',
    "visible SIM validation",
)
text = replace_once(
    text,
    '''            val newIdentityOverrides =\n                IdentityOverrides(''',
    '''            val previousVisibleSimCount = identityOverrides.visibleSimCount\n            val newIdentityOverrides =\n                IdentityOverrides(''',
    "visible SIM previous state",
)
text = replace_once(
    text,
    '''                    phoneNumber2 = newVars["ATTESTATION_ID_PHONE_NUMBER2"],\n                    serial = newVars["ATTESTATION_ID_SERIAL"],\n                )''',
    '''                    phoneNumber2 = newVars["ATTESTATION_ID_PHONE_NUMBER2"],\n                    serial = newVars["ATTESTATION_ID_SERIAL"],\n                    visibleSimCount = newVars["VISIBLE_SIM_COUNT"]?.toInt(),\n                )''',
    "parse visible SIM count",
)
text = replace_once(
    text,
    '''            identityOverrides = newIdentityOverrides\n            moduleHashFromVars = parsedModuleHash\n            stringToBytesCache.clear()''',
    '''            identityOverrides = newIdentityOverrides\n            moduleHashFromVars = parsedModuleHash\n            stringToBytesCache.clear()\n            if (previousVisibleSimCount != newIdentityOverrides.visibleSimCount) signalRuntimeController()''',
    "signal visible SIM lifecycle change",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Pure bounded visibility helper: easy to unit-test and allocation-free for no-op.
# ---------------------------------------------------------------------------
write(
    "service/src/main/java/cleveres/tricky/cleverestech/SubscriptionVisibility.kt",
    '''package cleveres.tricky.cleverestech\n\ninternal const val MAX_VISIBLE_SIM_COUNT = 8\n\ninternal fun boundedVisibleSubscriptionCount(\n    realCount: Int,\n    configuredLimit: Int?,\n): Int {\n    val real = realCount.coerceAtLeast(0)\n    val limit = configuredLimit ?: return real\n    return minOf(real, limit.coerceIn(0, MAX_VISIBLE_SIM_COUNT))\n}\n\ninternal fun <T> boundedVisibleSubscriptions(\n    realSubscriptions: List<T>,\n    configuredLimit: Int?,\n): List<T> {\n    val limit = configuredLimit ?: return realSubscriptions\n    val bounded = limit.coerceIn(0, MAX_VISIBLE_SIM_COUNT)\n    if (realSubscriptions.size <= bounded) return realSubscriptions\n    return realSubscriptions.subList(0, bounded)\n}\n''',
)


# ---------------------------------------------------------------------------
# ISub compile-only stub. Runtime transaction IDs still come from Android Stub.
# ---------------------------------------------------------------------------
write(
    "stub/src/main/java/com/android/internal/telephony/ISub.java",
    '''/* Minimal compile-only mirror of Android's hidden ISub surface used by the service. */\npackage com.android.internal.telephony;\n\nimport android.os.Binder;\nimport android.os.IBinder;\nimport android.os.IInterface;\nimport android.telephony.SubscriptionInfo;\nimport java.util.List;\n\npublic interface ISub extends IInterface {\n    abstract class Stub extends Binder implements ISub {\n        public static ISub asInterface(IBinder obj) {\n            throw new UnsupportedOperationException();\n        }\n    }\n\n    List<SubscriptionInfo> getActiveSubscriptionInfoList(\n            String callingPackage, String callingFeatureId, boolean isForAllProfiles);\n    int getActiveSubInfoCount(\n            String callingPackage, String callingFeatureId, boolean isForAllProfiles);\n    int getActiveSubInfoCountMax();\n}\n''',
)


# ---------------------------------------------------------------------------
# Subscription list/count interceptor. It never injects a process itself; it can
# only register after the existing opt-in Telephony Binder hook is available.
# ---------------------------------------------------------------------------
write(
    "service/src/main/java/cleveres/tricky/cleverestech/SubscriptionVisibilityInterceptor.kt",
    '''package cleveres.tricky.cleverestech\n\nimport android.os.IBinder\nimport android.os.Parcel\nimport android.os.ServiceManager\nimport android.telephony.SubscriptionInfo\nimport cleveres.tricky.cleverestech.binder.BinderInterceptor\nimport com.android.internal.telephony.ISub\n\nobject SubscriptionVisibilityInterceptor : BinderInterceptor() {\n    private val getActiveSubscriptionInfoListTransaction =\n        getTransactCode(ISub.Stub::class.java, "getActiveSubscriptionInfoList")\n    private val getActiveSubInfoCountTransaction =\n        getTransactCode(ISub.Stub::class.java, "getActiveSubInfoCount")\n    private val getActiveSubInfoCountMaxTransaction =\n        getTransactCode(ISub.Stub::class.java, "getActiveSubInfoCountMax")\n    private val interceptedCodes =\n        validTransactCodes(\n            getActiveSubscriptionInfoListTransaction,\n            getActiveSubInfoCountTransaction,\n            getActiveSubInfoCountMaxTransaction,\n        )\n\n    private var subscriptionService: IBinder? = null\n    private var controlEndpoint: IBinder? = null\n\n    @Volatile\n    private var registered = false\n\n    override fun onPreTransact(\n        target: IBinder,\n        code: Int,\n        flags: Int,\n        callingUid: Int,\n        callingPid: Int,\n        data: Parcel,\n    ): Result =\n        if (\n            registered &&\n            target === subscriptionService &&\n            code in interceptedCodes &&\n            Config.shouldInterceptSubscriptionVisibility &&\n            Config.getVisibleSimCount(callingUid) != null\n        ) {\n            Continue\n        } else {\n            Skip\n        }\n\n    override fun onPostTransact(\n        target: IBinder,\n        code: Int,\n        flags: Int,\n        callingUid: Int,\n        callingPid: Int,\n        data: Parcel,\n        reply: Parcel?,\n        resultCode: Int,\n    ): Result {\n        if (\n            !registered ||\n            target !== subscriptionService ||\n            code !in interceptedCodes ||\n            reply == null ||\n            resultCode != 0 ||\n            !Config.shouldInterceptSubscriptionVisibility\n        ) {\n            return Skip\n        }\n        val limit = Config.getVisibleSimCount(callingUid) ?: return Skip\n        val originalPosition = reply.dataPosition()\n        return try {\n            reply.readException()\n            when (code) {\n                getActiveSubscriptionInfoListTransaction -> {\n                    val original = reply.createTypedArrayList(SubscriptionInfo.CREATOR) ?: return Skip\n                    val visible = boundedVisibleSubscriptions(original, limit)\n                    if (visible.size == original.size) return Skip\n                    Parcel.obtain().also { replacement ->\n                        replacement.writeNoException()\n                        replacement.writeTypedList(visible)\n                    }.let { OverrideReply(0, it) }\n                }\n                getActiveSubInfoCountTransaction, getActiveSubInfoCountMaxTransaction -> {\n                    val original = reply.readInt()\n                    val visible = boundedVisibleSubscriptionCount(original, limit)\n                    if (visible == original) return Skip\n                    Parcel.obtain().also { replacement ->\n                        replacement.writeNoException()\n                        replacement.writeInt(visible)\n                    }.let { OverrideReply(0, it) }\n                }\n                else -> Skip\n            }\n        } catch (_: RuntimeException) {\n            Skip\n        } finally {\n            reply.setDataPosition(originalPosition)\n        }\n    }\n\n    @Synchronized\n    fun tryRun(): Boolean {\n        if (!Config.shouldInterceptSubscriptionVisibility) {\n            stop()\n            return true\n        }\n        val current = subscriptionService\n        if (registered && current != null && current.isBinderAlive) return true\n        registered = false\n\n        val service = ServiceManager.getService("isub") ?: return false\n        val control = getBinderControlEndpoint(service) ?: return false\n        if (!registerBinderInterceptor(control, service, this, interceptedCodes)) return false\n\n        subscriptionService = service\n        controlEndpoint = control\n        registered = true\n        if (!Config.shouldInterceptSubscriptionVisibility) {\n            stop()\n            return true\n        }\n        Logger.i("Subscription visibility interceptor registered")\n        return true\n    }\n\n    fun isRunning(): Boolean =\n        registered && subscriptionService?.isBinderAlive == true\n\n    @Synchronized\n    fun stop(): Boolean {\n        if (!registered) {\n            subscriptionService = null\n            controlEndpoint = null\n            return true\n        }\n        val target = subscriptionService\n        val control = controlEndpoint\n        if (target == null || control == null || !target.isBinderAlive) {\n            registered = false\n            subscriptionService = null\n            controlEndpoint = null\n            return true\n        }\n        if (!unregisterBinderInterceptor(control, target, this)) return false\n        registered = false\n        subscriptionService = null\n        controlEndpoint = null\n        return true\n    }\n\n    override fun onInterceptorReplaced() {\n        registered = false\n        subscriptionService = null\n        controlEndpoint = null\n        Config.signalRuntimeController()\n    }\n}\n''',
)


# ---------------------------------------------------------------------------
# Runtime controller: subscription visibility shares Telephony lifecycle.
# ---------------------------------------------------------------------------
rel = "service/src/main/java/cleveres/tricky/cleverestech/Main.kt"
text = read(rel)
text = replace_once(
    text,
    '''            var telSuccess = !Config.shouldInterceptTelephony || TelephonyInterceptor.isRunning()''',
    '''            var telSuccess =\n                !Config.shouldInterceptTelephony ||\n                    (\n                        TelephonyInterceptor.isRunning() &&\n                            (!Config.shouldInterceptSubscriptionVisibility || SubscriptionVisibilityInterceptor.isRunning())\n                    )''',
    "Main telephony health",
)
text = replace_once(
    text,
    '''                            telSuccess = TelephonyInterceptor.tryRunTelephonyInterceptor()''',
    '''                            telSuccess =\n                                TelephonyInterceptor.tryRunTelephonyInterceptor() &&\n                                    SubscriptionVisibilityInterceptor.tryRun()''',
    "Main telephony start",
)
text = replace_once(
    text,
    '''            if (!telephonyEnabled && (previousTelephonyState != false || telephonyStopPending)) {\n                val wasPending = telephonyStopPending\n                telephonyStopPending = !TelephonyInterceptor.stopTelephonyInterceptor()''',
    '''            if (!telephonyEnabled && (previousTelephonyState != false || telephonyStopPending)) {\n                val wasPending = telephonyStopPending\n                val subscriptionStopped = SubscriptionVisibilityInterceptor.stop()\n                telephonyStopPending = !subscriptionStopped || !TelephonyInterceptor.stopTelephonyInterceptor()''',
    "Main telephony stop order",
)
text = replace_once(
    text,
    '''            } else if (telephonyEnabled) {\n                telephonyStopPending = false\n            }\n            previousTelephonyState = if (telephonyStopPending) null else telephonyEnabled''',
    '''            } else if (telephonyEnabled) {\n                telephonyStopPending = false\n                if (!Config.shouldInterceptSubscriptionVisibility) {\n                    SubscriptionVisibilityInterceptor.stop()\n                }\n            }\n            previousTelephonyState = if (telephonyStopPending) null else telephonyEnabled''',
    "Main visible SIM disable while telephony remains enabled",
)
text = replace_once(
    text,
    '''                DrmInterceptor.stopDrmInterceptor()\n                Logger.i("Main: Runtime controller interrupted, shutting down")''',
    '''                SubscriptionVisibilityInterceptor.stop()\n                DrmInterceptor.stopDrmInterceptor()\n                Logger.i("Main: Runtime controller interrupted, shutting down")''',
    "Main shutdown subscription cleanup",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Web API: one field, Telephony group, and Random All.
# ---------------------------------------------------------------------------
rel = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"
text = read(rel)
text = replace_once(
    text,
    '''            .put("phone_number2", identity.phoneNumber2 ?: "")\n            .put("serial", identity.serial ?: "")''',
    '''            .put("phone_number2", identity.phoneNumber2 ?: "")\n            .put("serial", identity.serial ?: "")\n            .put("visible_sim_count", identity.visibleSimCount?.toString() ?: "")''',
    "identity JSON visible SIM",
)
text = replace_once(
    text,
    '''            "serial" -> RandomUtils.generateRandomSerial(12)\n            else -> throw IllegalArgumentException("Unsupported random identity field")''',
    '''            "serial" -> RandomUtils.generateRandomSerial(12)\n            "visible_sim_count" -> RandomUtils.choose(listOf("0", "1", "1", "1", "1", "2", "2")) ?: "1"\n            else -> throw IllegalArgumentException("Unsupported random identity field")''',
    "random visible SIM",
)
text = replace_once(
    text,
    '''                    "phone_number2",\n                    "serial",\n                )''',
    '''                    "phone_number2",\n                    "serial",\n                    "visible_sim_count",\n                )''',
    "random all visible SIM",
)
text = replace_once(
    text,
    '''            "sim2" -> putFields("imei2", "imsi2", "iccid2", "meid2", "phone_number2")\n            "device" -> putFields("serial")''',
    '''            "sim2" -> putFields("imei2", "imsi2", "iccid2", "meid2", "phone_number2")\n            "telephony" ->\n                putFields(\n                    "imei", "imsi", "iccid", "meid", "phone_number",\n                    "imei2", "imsi2", "iccid2", "meid2", "phone_number2",\n                    "visible_sim_count",\n                )\n            "device" -> putFields("serial")''',
    "random telephony group",
)
text = replace_once(
    text,
    '''            "phone_number", "phone_number2", "serial" -> putFields(normalized)''',
    '''            "phone_number", "phone_number2", "serial", "visible_sim_count" -> putFields(normalized)''',
    "random single visible SIM",
)
text = replace_once(
    text,
    '''                "phone_number2" to "ATTESTATION_ID_PHONE_NUMBER2",\n                "serial" to "ATTESTATION_ID_SERIAL",''',
    '''                "phone_number2" to "ATTESTATION_ID_PHONE_NUMBER2",\n                "serial" to "ATTESTATION_ID_SERIAL",\n                "visible_sim_count" to "VISIBLE_SIM_COUNT",''',
    "identity field mapping visible SIM",
)
write(rel, text)


# ---------------------------------------------------------------------------
# WebUI: visible count + group randomization + load/save/clear wiring.
# ---------------------------------------------------------------------------
rel = "module/template/webroot/index.html"
text = read(rel)
text = replace_once(
    text,
    '''</div>\n<div class="section-header">Device</div>\n<div><label for="inputSerial">Serial</label>''',
    '''</div>\n<div class="section-header">Telephony visibility</div>\n<div class="grid-2">\n    <div><label for="inputVisibleSimCount">Visible SIM count</label><div class="identity-input-action"><div class="identity-input-slot"><input type="number" id="inputVisibleSimCount" min="0" max="8" step="1" inputmode="numeric" aria-describedby="visibleSimScope"></div><button type="button" class="identity-random-btn" title="Random" aria-label="Random" onclick="runWithState(this, 'Generating...', () => randomizeIdentityField('visible_sim_count'))">Random</button></div></div>\n    <div id="visibleSimScope" class="scope-note" style="margin:0;align-self:end;">Limits the active subscription list returned to selected apps. It never creates SIMs that are not present.</div>\n</div>\n<div style="margin-top:10px;"><button type="button" onclick="runWithState(this, 'Generating...', () => generateRandomIdentity('telephony'))" style="width:100%;">Randomize Telephony</button></div>\n<div class="section-header">Device</div>\n<div><label for="inputSerial">Serial</label>''',
    "visible SIM UI",
)
text = replace_once(
    text,
    '''    inputPhoneNumber: 'phone_number', inputPhoneNumber2: 'phone_number2', inputSerial: 'serial'\n};''',
    '''    inputPhoneNumber: 'phone_number', inputPhoneNumber2: 'phone_number2', inputSerial: 'serial',\n    inputVisibleSimCount: 'visible_sim_count'\n};''',
    "load identity visible SIM",
)
text = replace_once(
    text,
    '''phone_number: 'inputPhoneNumber', phone_number2: 'inputPhoneNumber2', serial: 'inputSerial'\n        });''',
    '''phone_number: 'inputPhoneNumber', phone_number2: 'inputPhoneNumber2', serial: 'inputSerial',\nvisible_sim_count: 'inputVisibleSimCount'\n        });''',
    "random map visible SIM",
)
text = replace_once(
    text,
    '''    'inputIccid', 'inputIccid2', 'inputPhoneNumber', 'inputPhoneNumber2', 'inputSerial'\n].forEach(id => {''',
    '''    'inputIccid', 'inputIccid2', 'inputPhoneNumber', 'inputPhoneNumber2', 'inputSerial', 'inputVisibleSimCount'\n].forEach(id => {''',
    "clear visible SIM",
)
text = replace_once(
    text,
    '''    phone_number: 'inputPhoneNumber', phone_number2: 'inputPhoneNumber2', serial: 'inputSerial'\n};''',
    '''    phone_number: 'inputPhoneNumber', phone_number2: 'inputPhoneNumber2', serial: 'inputSerial',\n    visible_sim_count: 'inputVisibleSimCount'\n};''',
    "save visible SIM",
)
write(rel, text)


# ---------------------------------------------------------------------------
# Localization: all built-in non-English locales receive the same new keys.
# ---------------------------------------------------------------------------
rel = "module/template/webroot/ux.js"
text = read(rel)
locale_values = {
    "tr": ("Görünür SIM sayısı", "Seçili uygulamalara döndürülen etkin abonelik listesini sınırlar. Var olmayan SIM oluşturmaz.", "Telefon kimliğini rastgeleleştir"),
    "zh-CN": ("可见 SIM 数量", "限制向所选应用返回的活动订阅列表。不会创建实际不存在的 SIM。", "随机化电话身份"),
    "es": ("Cantidad de SIM visibles", "Limita la lista de suscripciones activas devuelta a las apps seleccionadas. Nunca crea SIM inexistentes.", "Aleatorizar telefonía"),
    "de": ("Sichtbare SIM-Anzahl", "Begrenzt die Liste aktiver Abonnements für ausgewählte Apps. Es werden keine nicht vorhandenen SIMs erzeugt.", "Telefonie randomisieren"),
    "ru": ("Количество видимых SIM", "Ограничивает список активных подписок для выбранных приложений. Не создаёт несуществующие SIM.", "Рандомизировать телефонию"),
    "id": ("Jumlah SIM terlihat", "Membatasi daftar langganan aktif yang dikembalikan ke aplikasi terpilih. Tidak membuat SIM yang sebenarnya tidak ada.", "Acak identitas telepon"),
    "hi": ("दिखाई देने वाली SIM संख्या", "चुने गए ऐप्स को लौटाई जाने वाली सक्रिय सदस्यता सूची सीमित करता है। मौजूद न होने वाली SIM नहीं बनाता।", "टेलीफोनी रैंडमाइज़ करें"),
    "ar": ("عدد شرائح SIM الظاهرة", "يحد من قائمة الاشتراكات النشطة المعادة للتطبيقات المحددة، ولا ينشئ شرائح SIM غير موجودة.", "توليد هوية الهاتف عشوائياً"),
}
for locale, values in locale_values.items():
    marker = f"        {repr(locale) if '-' in locale else locale}: {{" if locale != "zh-CN" else "        'zh-CN': {"
    start = text.find(marker)
    if start < 0:
        raise RuntimeError(f"locale marker missing: {locale}")
    needle = "'Identity value randomized':"
    pos = text.find(needle, start)
    if pos < 0:
        raise RuntimeError(f"randomized key missing: {locale}")
    line_end = text.find("\n", pos)
    if line_end < 0:
        raise RuntimeError(f"locale line end missing: {locale}")
    visible, note, group = values
    addition = (\n        f"            'Visible SIM count': {visible!r}, "\n        f"'Limits the active subscription list returned to selected apps. It never creates SIMs that are not present.': {note!r}, "\n        f"'Randomize Telephony': {group!r},\n"\n    )
    text = text[: line_end + 1] + addition + text[line_end + 1 :]
write(rel, text)


# ---------------------------------------------------------------------------
# Unit tests: bounds, no-op identity, persistence, random scope.
# ---------------------------------------------------------------------------
write(
    "service/src/test/java/cleveres/tricky/cleverestech/SubscriptionVisibilityTest.kt",
    '''package cleveres.tricky.cleverestech\n\nimport org.junit.Assert.assertEquals\nimport org.junit.Assert.assertSame\nimport org.junit.Test\n\nclass SubscriptionVisibilityTest {\n    @Test\n    fun `missing limit is a zero allocation no-op`() {\n        val input = listOf("sim0", "sim1")\n        assertSame(input, boundedVisibleSubscriptions(input, null))\n        assertEquals(2, boundedVisibleSubscriptionCount(2, null))\n    }\n\n    @Test\n    fun `configured limit can only reduce real subscriptions`() {\n        val input = listOf("sim0", "sim1", "sim2")\n        assertEquals(listOf("sim0"), boundedVisibleSubscriptions(input, 1))\n        assertEquals(emptyList<String>(), boundedVisibleSubscriptions(input, 0))\n        assertSame(input, boundedVisibleSubscriptions(input, 8))\n        assertEquals(1, boundedVisibleSubscriptionCount(3, 1))\n        assertEquals(3, boundedVisibleSubscriptionCount(3, 8))\n        assertEquals(0, boundedVisibleSubscriptionCount(-1, 2))\n    }\n}\n''',
)

rel = "service/src/test/java/cleveres/tricky/cleverestech/WebServerIdentityTest.kt"
text = read(rel)
text = replace_once(
    text,
    '''                .put("phone_number2", "+12025550124")\n                .put("serial", "DEVICE_01")''',
    '''                .put("phone_number2", "+12025550124")\n                .put("serial", "DEVICE_01")\n                .put("visible_sim_count", "1")''',
    "Web identity save visible SIM",
)
text = replace_once(
    text,
    '''        assertEquals("+12025550124", saved.getString("phone_number2"))''',
    '''        assertEquals("+12025550124", saved.getString("phone_number2"))\n        assertEquals("1", saved.getString("visible_sim_count"))\n        assertEquals(1, Config.getIdentityOverrides().visibleSimCount)''',
    "Web identity assert visible SIM",
)
text = replace_once(
    text,
    '''        assertEquals(400, postIdentity(JSONObject().put("unknown", "value")).first)''',
    '''        assertEquals(400, postIdentity(JSONObject().put("visible_sim_count", "9")).first)\n        assertEquals(400, postIdentity(JSONObject().put("visible_sim_count", "-1")).first)\n        assertEquals(400, postIdentity(JSONObject().put("unknown", "value")).first)''',
    "Web identity reject visible SIM",
)
text = replace_once(
    text,
    '''        assertTrue(json.getString("iccid2").isNotBlank())''',
    '''        assertTrue(json.getString("iccid2").isNotBlank())\n        assertTrue(json.getInt("visible_sim_count") in 0..2)''',
    "random all visible SIM assertion",
)
text = replace_once(
    text,
    '''    @Test\n    fun `random template returns a bounded known template view`() {''',
    '''    @Test\n    fun `telephony random group includes visibility but not device serial`() {\n        val response = request("GET", "/api/random_identity?field=telephony")\n        assertEquals(200, response.first)\n        val json = JSONObject(response.second)\n        assertEquals(11, json.length())\n        assertTrue(json.has("imei"))\n        assertTrue(json.has("imei2"))\n        assertTrue(json.getInt("visible_sim_count") in 0..2)\n        assertFalse(json.has("serial"))\n    }\n\n    @Test\n    fun `random template returns a bounded known template view`() {''',
    "telephony random group test",
)
write(rel, text)

# WebUI regression test: new field and group route are statically wired.
rel = "module/webui-tests/identity-randomization.test.js"
text = read(rel)
text += '''\nassert(index.includes('id="inputVisibleSimCount"'), 'visible SIM count control must exist');\nassert(index.includes("randomizeIdentityField('visible_sim_count')"), 'visible SIM count must support single-field randomization');\nassert(index.includes("generateRandomIdentity('telephony')"), 'Telephony section must support grouped randomization');\nassert(index.includes("visible_sim_count: 'inputVisibleSimCount'"), 'random payload must map visible SIM count');\n'''
write(rel, text)
