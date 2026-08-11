from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    file = ROOT / path
    text = file.read_text()
    found = text.count(old)
    if found != count:
        raise RuntimeError(f"{path}: expected {count} occurrence(s), found {found}: {old[:100]!r}")
    file.write_text(text.replace(old, new, count))


# Config: Spoof Engine is identity-only. Core targeting, TEE/certificate work and
# keybox maintenance remain independent from it.
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''    /**
     * Master runtime gate for every spoofing interceptor and boot-time property
     * override. Installations create the flag by default; keeping the in-memory
     * default enabled preserves safe behavior until the initial configuration
     * snapshot has been loaded.
     */
    @Volatile
    var isSpoofEnabled = true
        private set
''',
    '''    /**
     * Opt-in identity gate. Core Keystore/TEE interception and boot protection
     * remain active independently of this switch.
     */
    @Volatile
    var isSpoofEnabled = false
        private set
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''    fun shouldApplyTelephonyPrivacy(uid: Int): Boolean =
        (isTelephonyEnabled || getAppPrivacyMode(uid) != AppPrivacyMode.INHERIT) && isTargetedUid(uid)
''',
    '''    fun shouldApplyTelephonyPrivacy(uid: Int): Boolean =
        isSpoofEnabled &&
            (isTelephonyEnabled || getAppPrivacyMode(uid) != AppPrivacyMode.INHERIT) &&
            isTargetedUid(uid)
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''        isSpoofEnabled = enabled
        KeyboxAutoCleaner.setEnabled(enabled && isRegularFlagFile(File(root, AUTO_KEYBOX_CHECK_FILE)))
        Logger.i("Spoof engine is ${if (enabled) "enabled" else "disabled"}")
''',
    '''        isSpoofEnabled = enabled
        KeyboxAutoCleaner.setEnabled(isRegularFlagFile(File(root, AUTO_KEYBOX_CHECK_FILE)))
        Logger.i("Identity Spoof Engine is ${if (enabled) "enabled" else "disabled"}; core protection is unchanged")
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''            AUTO_KEYBOX_CHECK_FILE -> KeyboxAutoCleaner.setEnabled(isSpoofEnabled && file != null)
''',
    '''            AUTO_KEYBOX_CHECK_FILE -> KeyboxAutoCleaner.setEnabled(file != null)
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''    fun getAttestationId(
        tag: String,
        uid: Int,
    ): ByteArray? {
        when (getAppPrivacyMode(uid)) {
''',
    '''    fun getAttestationId(
        tag: String,
        uid: Int,
    ): ByteArray? {
        if (!isSpoofEnabled) return null
        when (getAppPrivacyMode(uid)) {
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''        KeyboxAutoCleaner.setEnabled(isSpoofEnabled && isRegularFlagFile(File(root, AUTO_KEYBOX_CHECK_FILE)))
''',
    '''        KeyboxAutoCleaner.setEnabled(isRegularFlagFile(File(root, AUTO_KEYBOX_CHECK_FILE)))
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''                    "ATTESTATION_ID_ICCID" to RandomUtils.generateLuhn(20, "8901"),
                    "ATTESTATION_ID_ICCID2" to RandomUtils.generateLuhn(20, "8901"),
''',
    '''                    "ATTESTATION_ID_ICCID" to RandomUtils.generateLuhn(20, "8901"),
                    "ATTESTATION_ID_ICCID2" to RandomUtils.generateLuhn(20, "8901"),
                    "ATTESTATION_ID_MEID" to RandomUtils.generateHex(14),
                    "ATTESTATION_ID_MEID2" to RandomUtils.generateHex(14),
                    "ATTESTATION_ID_PHONE_NUMBER" to "+1${RandomUtils.generateDigits(10)}",
                    "ATTESTATION_ID_PHONE_NUMBER2" to "+1${RandomUtils.generateDigits(10)}",
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''                AUTO_KEYBOX_CHECK_FILE -> KeyboxAutoCleaner.setEnabled(isSpoofEnabled && isRegularFlagFile(f))
''',
    '''                AUTO_KEYBOX_CHECK_FILE -> KeyboxAutoCleaner.setEnabled(isRegularFlagFile(f))
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''    private fun isTargetedUid(callingUid: Int): Boolean {
        if (!isSpoofEnabled || callingUid < FIRST_APPLICATION_UID) return false
''',
    '''    private fun isTargetedUid(callingUid: Int): Boolean {
        if (callingUid < FIRST_APPLICATION_UID) return false
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''    fun needHack(callingUid: Int): Boolean = !isTeeBrokenMode && isTargetedUid(callingUid)
''',
    '''    fun needHack(callingUid: Int): Boolean = isTargetedUid(callingUid)
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''        isGlobalMode = false
        isSpoofEnabled = true
        isBuildIdentityEnabled = false
''',
    '''        isGlobalMode = false
        isSpoofEnabled = false
        isBuildIdentityEnabled = false
''',
)

# Fix nullable month checks in the newly added Auto Identity resolver.
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/AutoIdentityManager.kt",
    '''                    if (month in 1..12) LocalDate.of(year, month!!, 5) else null
''',
    '''                    if (month != null && month in 1..12) LocalDate.of(year, month, 5) else null
''',
    count=2,
)

# Web server: injectable Auto Identity resolver, full random identifiers, and
# an opt-in endpoint that persists Pixel beta/canary build identity without
# enabling the identity engine itself.
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt",
    '''    private val crlFetcher: () -> Set<String>? = { KeyboxVerifier.fetchCrl() },
    private val permissionSetter: (File, Int) -> Unit = { f, m ->
''',
    '''    private val crlFetcher: () -> Set<String>? = { KeyboxVerifier.fetchCrl() },
    private val autoIdentityFetcher: () -> AutoIdentityManager.Result = { AutoIdentityManager.fetchLatest() },
    private val permissionSetter: (File, Int) -> Unit = { f, m ->
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt",
    '''                json.put("iccid", RandomUtils.generateLuhn(20, "8901"))
                json.put("iccid2", RandomUtils.generateLuhn(20, "8901"))
                return secureResponse(Response.Status.OK, "application/json", json.toString())
            }
            return secureResponse(Response.Status.NOT_FOUND, "text/plain", "No templates found")
        }

        if (uri == "/api/packages" && method == Method.GET) {
''',
    '''                json.put("iccid", RandomUtils.generateLuhn(20, "8901"))
                json.put("iccid2", RandomUtils.generateLuhn(20, "8901"))
                json.put("meid", RandomUtils.generateHex(14))
                json.put("meid2", RandomUtils.generateHex(14))
                json.put("phone_number", "+1${RandomUtils.generateDigits(10)}")
                json.put("phone_number2", "+1${RandomUtils.generateDigits(10)}")
                return secureResponse(Response.Status.OK, "application/json", json.toString())
            }
            return secureResponse(Response.Status.NOT_FOUND, "text/plain", "No templates found")
        }

        if (uri == "/api/auto_identity" && method == Method.POST) {
            return try {
                val resolved = autoIdentityFetcher()
                val updates = linkedMapOf<String, String?>("TEMPLATE" to null)
                resolved.buildVars().forEach { (key, value) ->
                    require(Config.isValidBuildVarEntry(key, value)) { "Auto Identity returned an invalid build field" }
                    updates[key] = value
                }
                if (!saveIdentityUpdates(updates)) {
                    return secureResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Auto Identity could not be saved")
                }
                SecureFile.touch(File(configDir, "spoof_build_identity"), 384)
                Config.refreshRuntimeSetting("spoof_build_identity")
                val json =
                    JSONObject()
                        .put("model", resolved.model)
                        .put("product", resolved.product)
                        .put("device", resolved.device)
                        .put("fingerprint", resolved.fingerprint)
                        .put("build_id", resolved.buildId)
                        .put("incremental", resolved.incremental)
                        .put("release", resolved.release ?: "")
                        .put("security_patch", resolved.securityPatch)
                        .put("security_patch_estimated", resolved.securityPatchEstimated)
                secureResponse(Response.Status.OK, "application/json", json.toString())
            } catch (error: IOException) {
                Logger.e("Auto Identity source lookup failed", error)
                secureResponse(Response.Status.SERVICE_UNAVAILABLE, "text/plain", "Auto Identity source is unavailable")
            } catch (error: Exception) {
                Logger.e("Auto Identity failed", error)
                secureResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Auto Identity failed")
            }
        }

        if (uri == "/api/packages" && method == Method.GET) {
''',
)
replace(
    "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt",
    '''                            "ATTESTATION_ID_ICCID" to RandomUtils.generateLuhn(20, "8901"),
                            "ATTESTATION_ID_ICCID2" to RandomUtils.generateLuhn(20, "8901"),
''',
    '''                            "ATTESTATION_ID_ICCID" to RandomUtils.generateLuhn(20, "8901"),
                            "ATTESTATION_ID_ICCID2" to RandomUtils.generateLuhn(20, "8901"),
                            "ATTESTATION_ID_MEID" to RandomUtils.generateHex(14),
                            "ATTESTATION_ID_MEID2" to RandomUtils.generateHex(14),
                            "ATTESTATION_ID_PHONE_NUMBER" to "+1${RandomUtils.generateDigits(10)}",
                            "ATTESTATION_ID_PHONE_NUMBER2" to "+1${RandomUtils.generateDigits(10)}",
''',
)

# Mobile navigation moves to the bottom. Desktop remains unchanged.
replace(
    "module/template/webroot/index.html",
    '''        @media screen and (max-width: 380px) {
''',
    '''        @media screen and (max-width: 700px) {
.tabs { position: fixed; top: auto; bottom: 0; left: 0; right: 0; z-index: 1000; border-top: 1px solid var(--border); border-bottom: 0; padding-bottom: env(safe-area-inset-bottom); background: var(--panel); }
.content { padding-bottom: calc(104px + env(safe-area-inset-bottom)); }
.tab { min-height: 54px; padding: 12px 16px; }
        }
        @media screen and (max-width: 380px) {
''',
)
replace(
    "module/template/webroot/index.html",
    '''.content { padding-bottom: max(64px, env(safe-area-inset-bottom)); }
''',
    '''.content { padding-bottom: max(96px, calc(64px + env(safe-area-inset-bottom))); }
''',
)

# Dashboard: core protection is read-only and always active. Identity controls
# are moved to the bottom of the Identity tab.
replace(
    "module/template/webroot/index.html",
    '''        <div class="panel master-panel">
<div class="row" style="margin-bottom:8px;">
    <label class="master-copy" for="spoof_enabled"><strong>Spoof Engine</strong><span>Master control for attestation, telephony, build identity, and boot-property spoofing.</span></label>
    <input type="checkbox" class="toggle" id="spoof_enabled" data-setting="spoof_enabled" onchange="toggle('spoof_enabled', this)" aria-describedby="engineRuntimeNote">
</div>
<div id="engineRuntimeNote" class="scope-note" style="margin:0;">When paused, Binder interceptors are unregistered, native hooks enter an atomic fast path, and the scheduled keybox worker stops. Reboot once to undo boot-time property views and release injected libraries.</div>
        </div>
''',
    '''        <div class="panel">
<h3>Core Protection</h3>
<div class="scope-note" style="margin:0;"><strong style="color:var(--success);">Always active.</strong> Bootloader/verified-boot property compatibility and Keystore/TEE certificate protection are core module behavior. They have no on/off switch and continue working when Identity Engine is disabled. Hardware bootloader and root-of-trust state are not physically changed.</div>
        </div>
''',
)
replace(
    "module/template/webroot/index.html",
    '''    <div style="font-size: 0.8em; color: #888; text-transform: uppercase;">Spoof Engine</div>
    <div id="status_engine" style="font-weight: bold; color: var(--danger); margin-top: 5px; background: rgba(239, 68, 68, 0.1); padding: 5px; border-radius: 4px;">PAUSED</div>
''',
    '''    <div style="font-size: 0.8em; color: #888; text-transform: uppercase;">Identity Engine</div>
    <div id="status_engine" style="font-weight: bold; color: #888; margin-top: 5px; background: rgba(255, 255, 255, 0.05); padding: 5px; border-radius: 4px;">OFF</div>
''',
)
replace(
    "module/template/webroot/index.html",
    '''        <div class="panel">
<h3>Quick Profile</h3>
<div class="row">
    <select id="profileSelect" style="flex: 1; margin-right: 10px; min-height: 44px; padding: 12px 14px; background: var(--input-bg); border: 1px solid var(--border); color: #fff; border-radius: 6px;">
        <option value="">Select a Profile...</option>
        <option value="maximum">Maximum Compatibility</option>
        <option value="daily">Daily Compatibility</option>
        <option value="minimal">Minimal (substitution off)</option>
        <option value="default">Default (targeted)</option>
    </select>
    <button onclick="applySelectedProfile(this)" style="min-height: 44px;">Apply</button>
</div>
<div style="font-size:0.8em; color:#888; margin-top:5px;">Applying a profile will overwrite current settings below.</div>
        </div>
''',
    '''''',
)
replace(
    "module/template/webroot/index.html",
    '''        <div class="panel">
<h3>System Control</h3>
<div class="row"><label for="global_mode">Global Mode</label><input type="checkbox" class="toggle" id="global_mode" data-setting="global_mode" onchange="toggle('global_mode', this)"></div>
<div class="row"><label for="tee_broken_mode">Disable Certificate Substitution (Safe Mode)</label><input type="checkbox" class="toggle" id="tee_broken_mode" data-setting="tee_broken_mode" onchange="toggle('tee_broken_mode', this)"></div>
<div class="row"><label for="auto_keybox_check">Auto Keybox Check</label><input type="checkbox" class="toggle" id="auto_keybox_check" data-setting="auto_keybox_check" onchange="toggle('auto_keybox_check', this)"></div>
<div class="row"><label for="random_on_boot">Refresh Identity on Boot</label><input type="checkbox" class="toggle" id="random_on_boot" data-setting="random_on_boot" onchange="toggle('random_on_boot', this)"></div>
<div class="row"><label for="telephony">Telephony Identifier Interception</label><input type="checkbox" class="toggle" id="telephony" data-setting="telephony" onchange="toggle('telephony', this)"></div>
<div class="section-header">Compatibility passthrough</div>
<div class="row"><label for="rkp_passthrough">RKP Passthrough</label><input type="checkbox" class="toggle" id="rkp_passthrough" data-setting="rkp_passthrough" onchange="toggle('rkp_passthrough', this)"></div>
<div class="row"><label for="drm_passthrough">DRM App Passthrough</label><input type="checkbox" class="toggle" id="drm_passthrough" data-setting="drm_passthrough" onchange="toggle('drm_passthrough', this)"></div>
<div style="font-size:0.8em; color:#888; margin-top:5px;">RKP service packages are always protected from substitution. RKP passthrough also preserves generated-key responses. DRM passthrough excludes packages in drm_packages.txt.</div>
<div class="section-header">Boot Properties</div>
<div class="row"><label for="spoof_build_identity">Template Build Identity (Fingerprint)</label><input type="checkbox" class="toggle" id="spoof_build_identity" data-setting="spoof_build_identity" onchange="toggle('spoof_build_identity', this)"></div>
<div class="row"><label for="hide_sensitive_props">Hide Sensitive Props</label><input type="checkbox" class="toggle" id="hide_sensitive_props" data-setting="hide_sensitive_props" onchange="toggle('hide_sensitive_props', this)"></div>
<div class="row"><label for="spoof_region_cn">Spoof Region (CN)</label><input type="checkbox" class="toggle" id="spoof_region_cn" data-setting="spoof_region_cn" onchange="toggle('spoof_region_cn', this)"></div>
<div class="row"><label for="bootPropsMode">Boot Property Policy</label><select id="bootPropsMode" style="width:auto; min-width:150px;" onchange="saveBootPropsMode(this)"><option value="auto">Automatic</option><option value="force">Always apply</option><option value="disable">Disabled</option></select></div>
<div style="font-size:0.8em; color:#888; margin-top:5px;">Build identity applies the selected template fingerprint and app-visible android.os.Build fields before Zygote. Boot-property changes require a reboot. Automatic mode avoids known vendor and overlapping identity-provider conflicts. These controls do not relock the hardware bootloader or alter the TEE root of trust.</div>
<div style="margin-top:20px; border-top: 1px solid var(--border); padding-top: 15px;">
    <div class="row"><span id="keyboxStatus" style="font-size:0.9em; color:var(--success);">Active</span><button onclick="runWithState(this, 'Reloading...', reloadConfig)">Reload Config</button></div>
</div>
        </div>
''',
    '''        <div class="panel">
<h3>System Control</h3>
<div class="row"><label for="global_mode">Global Mode</label><input type="checkbox" class="toggle" id="global_mode" data-setting="global_mode" onchange="toggle('global_mode', this)"></div>
<div class="row"><label for="auto_keybox_check">Auto Keybox Check</label><input type="checkbox" class="toggle" id="auto_keybox_check" data-setting="auto_keybox_check" onchange="toggle('auto_keybox_check', this)"></div>
<div class="section-header">Compatibility passthrough</div>
<div class="row"><label for="rkp_passthrough">RKP Passthrough</label><input type="checkbox" class="toggle" id="rkp_passthrough" data-setting="rkp_passthrough" onchange="toggle('rkp_passthrough', this)"></div>
<div class="row"><label for="drm_passthrough">DRM App Passthrough</label><input type="checkbox" class="toggle" id="drm_passthrough" data-setting="drm_passthrough" onchange="toggle('drm_passthrough', this)"></div>
<div style="font-size:0.8em; color:#888; margin-top:5px;">RKP service packages are always protected from substitution. DRM passthrough excludes packages in drm_packages.txt. Neither setting disables core boot/TEE protection.</div>
<div style="margin-top:20px; border-top: 1px solid var(--border); padding-top: 15px;">
    <div class="row"><span id="keyboxStatus" style="font-size:0.9em; color:var(--success);">Active</span><button onclick="runWithState(this, 'Reloading...', reloadConfig)">Reload Config</button></div>
</div>
        </div>
''',
)
replace(
    "module/template/webroot/index.html",
    '''<div class="scope-note">Applying a template persists its fingerprint and build fields. Enable Template Build Identity and reboot to expose them through android.os.Build. Android ID remains Android's per-app SSAID, and the actual kernel uname remains unchanged. System, vendor, and boot/kernel attestation patch levels are controlled in security_patch.txt.</div>
<div class="grid-2"><button onclick="runWithState(this, 'Generating...', generateRandomIdentity)" class="primary">Generate Random</button><button onclick="runWithState(this, 'Saving...', applySpoofing)">Apply Identity</button></div>
''',
    '''<div class="scope-note">Applying a template persists its fingerprint and build fields. Build Identity at Boot requires Identity Engine and a reboot. Android ID remains Android's per-app SSAID, and the actual kernel uname remains unchanged.</div>
<div style="display:flex; gap:10px; flex-wrap:wrap;"><button onclick="runWithState(this, 'Generating...', generateRandomIdentity)" class="primary" style="flex:1;">Randomize All Identifiers</button><button onclick="runWithState(this, 'Fetching...', applyAutoIdentity)" style="flex:1;">Auto Identity (Pixel Beta)</button><button onclick="runWithState(this, 'Saving...', applySpoofing)" style="flex:1;">Apply Identity</button></div>
<div class="scope-note" style="margin-top:12px; margin-bottom:0;"><strong>Auto Identity:</strong> for Play Integrity it pulls a current Pixel beta/canary ROM identity from Google's public metadata. Recommended only if you use a Custom ROM. The result is saved locally; enable Identity Engine and reboot to expose build fields.</div>
''',
)
replace(
    "module/template/webroot/index.html",
    '''<div class="identity-actions"><button type="button" onclick="const btn = this; requireConfirm(btn, () => clearSpoofingInputs(), 'Confirm Clear')" style="background:transparent; border:1px solid var(--danger); color:var(--danger); min-height:44px; padding:0 20px;">Clear All</button><button onclick="runWithState(this, 'Saving...', applySpoofing)" class="danger">Apply Identity</button></div>
        </div>
    </div>

    <div id="apps" class="content" role="tabpanel" aria-labelledby="tab_apps">
''',
    '''<div class="identity-actions" style="flex-wrap:wrap;"><button type="button" onclick="runWithState(this, 'Generating...', generateRandomIdentity)">Randomize All</button><button type="button" onclick="const btn = this; requireConfirm(btn, () => clearSpoofingInputs(), 'Confirm Clear')" style="background:transparent; border:1px solid var(--danger); color:var(--danger); min-height:44px; padding:0 20px;">Clear All</button><button onclick="runWithState(this, 'Saving...', applySpoofing)" class="danger">Apply Identity</button></div>
        </div>
        <div class="panel">
<h3>Identity Controls</h3>
<div class="row"><label for="spoof_enabled"><strong style="color:#fff;">Identity Spoof Engine</strong><span class="res-desc">Enables only identity overrides. Core bootloader/TEE protection remains active when this is off.</span></label><input type="checkbox" class="toggle" id="spoof_enabled" data-setting="spoof_enabled" onchange="toggle('spoof_enabled', this)"></div>
<div class="row"><label for="spoof_build_identity">Build Identity at Boot</label><input type="checkbox" class="toggle" id="spoof_build_identity" data-setting="spoof_build_identity" onchange="toggle('spoof_build_identity', this)"></div>
<div class="row"><label for="random_on_boot">Refresh Identity on Boot</label><input type="checkbox" class="toggle" id="random_on_boot" data-setting="random_on_boot" onchange="toggle('random_on_boot', this)"></div>
<div class="row"><label for="telephony">Telephony Identifier Interception</label><input type="checkbox" class="toggle" id="telephony" data-setting="telephony" onchange="toggle('telephony', this)"></div>
<div class="row"><label for="spoof_region_cn">Spoof Region (CN)</label><input type="checkbox" class="toggle" id="spoof_region_cn" data-setting="spoof_region_cn" onchange="toggle('spoof_region_cn', this)"></div>
<div class="scope-note" style="margin:0;">These switches affect identity only. Bootloader/verified-boot property hiding and Keystore/TEE certificate protection are always-on core features.</div>
        </div>
    </div>

    <div id="apps" class="content" role="tabpanel" aria-labelledby="tab_apps">
''',
)

# Client setting list exposes no switches for always-on core protection.
replace(
    "module/template/webroot/index.html",
    '''        const WEB_UI_SETTINGS = ['spoof_enabled', 'spoof_build_identity', 'global_mode', 'tee_broken_mode', 'auto_keybox_check', 'random_on_boot', 'hide_sensitive_props', 'spoof_region_cn', 'telephony', 'rkp_passthrough', 'drm_passthrough'];
''',
    '''        const WEB_UI_SETTINGS = ['spoof_enabled', 'spoof_build_identity', 'global_mode', 'auto_keybox_check', 'random_on_boot', 'spoof_region_cn', 'telephony', 'rkp_passthrough', 'drm_passthrough'];
''',
)
replace(
    "module/template/webroot/index.html",
    '''status.innerText = enabled ? 'RUNNING' : 'PAUSED';
status.style.color = enabled ? 'var(--success)' : 'var(--danger)';
status.style.background = enabled ? 'rgba(74, 222, 128, 0.1)' : 'rgba(239, 68, 68, 0.1)';
''',
    '''status.innerText = enabled ? 'IDENTITY ON' : 'OFF';
status.style.color = enabled ? 'var(--success)' : '#888';
status.style.background = enabled ? 'rgba(74, 222, 128, 0.1)' : 'rgba(255, 255, 255, 0.05)';
''',
)
replace(
    "module/template/webroot/index.html",
    '''    determineActiveProfile(data);
''',
    '''''',
)
replace(
    "module/template/webroot/index.html",
    '''    if (setting === 'spoof_enabled') {
        notify(requestedValue ? 'Spoof Engine resumed' : 'Spoof Engine paused; reboot to clear boot-time property views');
''',
    '''    if (setting === 'spoof_enabled') {
        notify(requestedValue ? 'Identity Spoof Engine enabled' : 'Identity Spoof Engine disabled; core protection remains active');
''',
)
replace(
    "module/template/webroot/index.html",
    '''    document.getElementById('inputIccid').value = t.iccid || '';
    document.getElementById('inputIccid2').value = t.iccid2 || '';
    document.getElementById('inputSerial').value = t.serial || '';
    ['inputMeid', 'inputMeid2', 'inputPhoneNumber', 'inputPhoneNumber2'].forEach(id => {
        document.getElementById(id).value = '';
    });
''',
    '''    document.getElementById('inputIccid').value = t.iccid || '';
    document.getElementById('inputIccid2').value = t.iccid2 || '';
    document.getElementById('inputMeid').value = t.meid || '';
    document.getElementById('inputMeid2').value = t.meid2 || '';
    document.getElementById('inputPhoneNumber').value = t.phone_number || '';
    document.getElementById('inputPhoneNumber2').value = t.phone_number2 || '';
    document.getElementById('inputSerial').value = t.serial || '';
''',
)
replace(
    "module/template/webroot/index.html",
    '''        async function verifyKeyboxes() {
''',
    '''        async function applyAutoIdentity() {
const res = await fetchAuth('/api/auto_identity', { method: 'POST', timeoutMs: 120000 });
if (!res.ok) throw new Error(await res.text());
const data = await res.json();
const sel = document.getElementById('templateSelect');
sel.value = '';
document.getElementById('pModel').innerText = String(data.model || 'Pixel Beta') + ' (Auto Identity)';
document.getElementById('pManuf').innerText = 'Google';
document.getElementById('pFing').innerText = String(data.fingerprint || '');
syncSettingControls('spoof_build_identity', true);
const patchNote = data.security_patch_estimated ? ' (estimated patch)' : '';
notify('Auto Identity saved for ' + String(data.model || 'Pixel Beta') + patchNote + '; reboot required');
        }

        async function verifyKeyboxes() {
''',
)

# Runtime health now judges the always-on core independently from Identity Engine.
replace(
    "module/template/webroot/index.html",
    '''    if (!data.spoof_enabled) {
        state = 'error';
        badge = 'PAUSED';
        message = 'Spoof Engine is paused, so runtime interception paths are parked.';
    } else if (!keystoreRunning) {
''',
    '''    if (!keystoreRunning) {
''',
)
replace(
    "module/template/webroot/index.html",
    '''    } else if (data.tee_broken_mode) {
        state = 'warn';
        badge = 'SAFE MODE';
        message = 'Certificate Safe Mode is enabled, so certificate substitution is intentionally disabled.';
''',
    '''''',
)
replace(
    "module/template/webroot/index.html",
    '''    healthText.textContent = message + ' Hardware bootloader and root-of-trust state remain genuine.';
''',
    '''    healthText.textContent = message + ' Core boot/TEE compatibility remains active independently of Identity Engine; hardware bootloader and root-of-trust state remain genuine.';
''',
)
replace(
    "module/template/webroot/index.html",
    '''    { id: 'spoof_enabled', name: 'Spoof Engine', activity: data.spoof_enabled ? (keystoreRunning ? 'Keystore interceptor operational' : (nativeActive ? 'Native active; registration pending' : 'Configured; native runtime unavailable')) : 'Interceptors parked', scope: 'All spoof and hook paths', desc: 'Master switch; operational readiness requires a live Keystore Binder registration.' },
''',
    '''    { id: 'spoof_enabled', name: 'Identity Spoof Engine', activity: data.spoof_enabled ? 'Identity overrides enabled' : 'Identity overrides off', scope: 'Attestation, telephony and build identity only', desc: 'Does not disable the core Keystore/TEE or boot protection paths.' },
''',
)
replace(
    "module/template/webroot/index.html",
    '''    { id: 'telephony_runtime', name: 'Telephony Runtime', activity: data.telephony ? (telephonyRunning ? 'Registered and Binder alive' : 'Enabled but not operational') : 'Disabled', scope: 'Phone subscription Binder lifecycle', desc: 'Reports the independent telephony registration state.' },
''',
    '''    { id: 'telephony_runtime', name: 'Telephony Runtime', activity: data.spoof_enabled && data.telephony ? (telephonyRunning ? 'Registered and Binder alive' : 'Enabled but not operational') : 'Disabled', scope: 'Phone subscription Binder lifecycle', desc: 'Identity-only Binder path; it is parked while Identity Engine is off.' },
''',
)
replace(
    "module/template/webroot/index.html",
    '''    { id: 'tee_broken_mode', name: 'Certificate Safe Mode', activity: 'No certificate rewrite', scope: 'Keystore interception', desc: 'Keeps genuine KeyMint responses when certificate substitution is paused.' },
''',
    '''''',
)
replace(
    "module/template/webroot/index.html",
    '''    { id: 'auto_keybox_check', name: 'Automatic Keybox Check', activity: data.spoof_enabled && data.auto_keybox_check ? 'Scheduled background check' : 'Worker stopped', scope: 'Authorized key material', desc: 'Revalidates key material and revocation state at a bounded interval.' },
''',
    '''    { id: 'auto_keybox_check', name: 'Automatic Keybox Check', activity: data.auto_keybox_check ? 'Scheduled background check' : 'Worker stopped', scope: 'Authorized key material', desc: 'Core keybox maintenance; independent from Identity Engine.' },
''',
)
replace(
    "module/template/webroot/index.html",
    '''    { id: 'hide_sensitive_props', name: 'Boot-State Property View', activity: 'Boot only', scope: 'App-visible system properties', desc: 'Does not relock the bootloader or alter hardware attestation.' },
''',
    '''''',
)

# Config policy regression: identity switch and legacy safe-mode flag cannot
# disable the core target decision.
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/ConfigTargetStateTest.kt",
    '''    private fun createTargetState(hack: PackageTrie<Boolean>): Any {
''',
    '''    @Test
    fun `identity engine and legacy safe mode do not disable core targeting`() {
        val uid = 10_002
        mockPackage(uid, arrayOf("com.example.core"))
        setPrivateField(Config, "isGlobalMode", true)
        setPrivateField(Config, "isSpoofEnabled", false)
        setPrivateField(Config, "isTeeBrokenMode", true)

        assertTrue("Core targeting must remain active while identity spoofing is off", Config.needHack(uid))
    }

    private fun createTargetState(hack: PackageTrie<Boolean>): Any {
''',
)

# Identity values remain stored while the engine is off but are not exposed to
# attestation until the identity switch is enabled.
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/ConfigIdentityOverridesTest.kt",
    '''    @Test
    fun `identity snapshot is atomic and slot aware`() {
''',
    '''    @Test
    fun `identity engine gates attestation ids without clearing stored values`() {
        val root = createTempDir(prefix = "identity_engine_").apply { deleteOnExit() }
        Config.setRootForTesting(root)
        val imei = RandomUtils.generateLuhn(15, "35")
        val vars = File(root, "spoof_build_vars").apply { writeText("ATTESTATION_ID_IMEI=$imei\\n") }
        Config.updateBuildVars(vars)

        assertNull(Config.getAttestationId("IMEI", 10_001))
        File(root, "spoof_enabled").createNewFile()
        Config.refreshRuntimeSetting("spoof_enabled")
        assertEquals(imei, String(requireNotNull(Config.getAttestationId("IMEI", 10_001))))
    }

    @Test
    fun `identity snapshot is atomic and slot aware`() {
''',
)

# Web API tests use a deterministic Auto Identity result and cover all random fields.
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerIdentityTest.kt",
    '''        server = WebServer(0, configDir)
''',
    '''        server =
            WebServer(
                0,
                configDir,
                autoIdentityFetcher = {
                    AutoIdentityManager.Result(
                        model = "Pixel Test",
                        product = "test_beta",
                        device = "test",
                        fingerprint = "google/test_beta/test:CANARY/BP31.260801.001/12345678:user/release-keys",
                        buildId = "BP31.260801.001",
                        incremental = "12345678",
                        release = "17",
                        securityPatch = "2026-08-05",
                        securityPatchEstimated = false,
                    )
                },
            )
''',
)
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerIdentityTest.kt",
    '''    @Test
    fun `identity API refuses a symbolic link destination`() {
''',
    '''    @Test
    fun `random identity includes every attestation and telephony field`() {
        val response = request("GET", "/api/random_identity")
        assertEquals(200, response.first)
        val json = JSONObject(response.second)
        assertEquals(14, json.getString("meid").length)
        assertEquals(14, json.getString("meid2").length)
        assertTrue(json.getString("phone_number").startsWith("+1"))
        assertTrue(json.getString("phone_number2").startsWith("+1"))
        assertTrue(json.getString("imei").isNotBlank())
        assertTrue(json.getString("iccid2").isNotBlank())
    }

    @Test
    fun `auto identity persists Pixel beta build fields without enabling identity engine`() {
        val response = request("POST", "/api/auto_identity")
        assertEquals(200, response.first)
        val data = JSONObject(response.second)
        assertEquals("Pixel Test", data.getString("model"))
        val vars = File(configDir, "spoof_build_vars").readText()
        assertTrue(vars.contains("FINGERPRINT=google/test_beta/test:CANARY/BP31.260801.001/12345678:user/release-keys"))
        assertTrue(vars.contains("SECURITY_PATCH=2026-08-05"))
        assertTrue(File(configDir, "spoof_build_identity").isFile)
        assertFalse(File(configDir, "spoof_enabled").exists())
    }

    @Test
    fun `identity API refuses a symbolic link destination`() {
''',
)

# HTML tests now assert the always-on core contract, Auto Identity and bottom nav.
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerHtmlTest.kt",
    '''import org.junit.Assert.assertTrue
''',
    '''import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
''',
)
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerHtmlTest.kt",
    '''        assertTrue("Missing Generate Random Button", html.contains("generateRandomIdentity"))
        assertTrue("Missing Telephony Toggle", html.contains("id=\\\"telephony\\\""))
        assertTrue("Missing master Spoof Engine Toggle", html.contains("id=\\\"spoof_enabled\\\""))
        assertTrue("Missing build identity Toggle", html.contains("id=\\\"spoof_build_identity\\\""))
'''.replace('\\\\\\"','\\"'),
    '''        assertTrue("Missing Generate Random Button", html.contains("generateRandomIdentity"))
        assertTrue("Missing Auto Identity Button", html.contains("Auto Identity (Pixel Beta)"))
        assertTrue("Missing Custom ROM Auto Identity note", html.contains("Recommended only if you use a Custom ROM"))
        assertTrue("Missing Telephony Toggle", html.contains("id=\\\"telephony\\\""))
        assertTrue("Missing identity Spoof Engine Toggle", html.contains("id=\\\"spoof_enabled\\\""))
        assertTrue("Missing build identity Toggle", html.contains("id=\\\"spoof_build_identity\\\""))
'''.replace('\\\\\\"','\\"'),
)
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerHtmlTest.kt",
    '''        assertTrue("Safe mode label is missing", html.contains("Disable Certificate Substitution (Safe Mode)"))
''',
    '''        assertFalse("Core protection must not expose a safe-mode switch", html.contains("Disable Certificate Substitution (Safe Mode)"))
        assertFalse("Core property hiding must not expose a toggle", html.contains("id=\\\"hide_sensitive_props\\\""))
        assertTrue("Missing always-active core notice", html.contains("Bootloader/verified-boot property compatibility"))
        assertTrue("Missing mobile bottom navigation", html.contains(".tabs { position: fixed; top: auto; bottom: 0;"))
'''.replace('\\\\\\"','\\"'),
)

# UX setting and route contracts reflect the identity/core split.
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerUXTest.kt",
    '''                "global_mode",
                "tee_broken_mode",
                "auto_keybox_check",
                "random_on_boot",
                "hide_sensitive_props",
                "spoof_region_cn",
''',
    '''                "global_mode",
                "auto_keybox_check",
                "random_on_boot",
                "spoof_region_cn",
''',
)
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerUXTest.kt",
    '''        assertTrue(html.contains("id=\\\"bootPropsMode\\\""))
        assertTrue(html.contains("saveBootPropsMode(this)"))
'''.replace('\\\\\\"','\\"'),
    '''        assertFalse(html.contains("id=\\\"bootPropsMode\\\""))
        assertFalse(html.contains("data-setting=\\\"tee_broken_mode\\\""))
        assertFalse(html.contains("data-setting=\\\"hide_sensitive_props\\\""))
        assertTrue(html.contains(".tabs { position: fixed; top: auto; bottom: 0;"))
'''.replace('\\\\\\"','\\"'),
)
replace(
    "service/src/test/java/cleveres/tricky/cleverestech/WebServerUXTest.kt",
    '''                "/api/random_identity",
                "/api/packages",
''',
    '''                "/api/random_identity",
                "/api/auto_identity",
                "/api/packages",
''',
)

print("agent edits applied")
