from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one exact anchor, found {count}")
    return text.replace(old, new, 1)


def regex_once(text, pattern, replacement, label, flags=0):
    result, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise RuntimeError(f"{label}: expected one regex anchor, found {count}")
    return result


# ---------------------------------------------------------------------------
# Service: bounded, on-demand random identity API. No background work is added.
# ---------------------------------------------------------------------------
web_rel = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"
web = read(web_rel)

identity_tail = '''            .put("phone_number2", identity.phoneNumber2 ?: "")
            .put("serial", identity.serial ?: "")
    }

    private fun parseIdentityUpdates(json: String): Map<String, String?> {'''
identity_helpers = '''            .put("phone_number2", identity.phoneNumber2 ?: "")
            .put("serial", identity.serial ?: "")
    }

    private fun randomIdentityValue(field: String): String =
        when (field) {
            "imei", "imei2" -> RandomUtils.generateLuhn(15, "35")
            "imsi", "imsi2" -> RandomUtils.generateDigits(15, "310260")
            "iccid", "iccid2" -> RandomUtils.generateLuhn(20, "8901")
            "meid", "meid2" -> RandomUtils.generateHex(14)
            "phone_number", "phone_number2" -> "+1${RandomUtils.generateDigits(10)}"
            "serial" -> RandomUtils.generateRandomSerial(12)
            else -> throw IllegalArgumentException("Unsupported random identity field")
        }

    private fun randomTemplateJson(): JSONObject? {
        val template = RandomUtils.choose(DeviceTemplateManager.listTemplates()) ?: return null
        return JSONObject()
            .put("id", template.id)
            .put("model", template.model)
            .put("manufacturer", template.manufacturer)
            .put("fingerprint", template.fingerprint)
            .put("securityPatch", template.securityPatch)
    }

    private fun randomIdentityJson(selection: String): JSONObject? {
        val normalized = selection.trim().lowercase()
        val json = JSONObject()

        fun copyTemplateIfAvailable(required: Boolean): Boolean {
            val template = randomTemplateJson()
            if (template == null) return !required
            val keys = template.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                json.put(key, template.get(key))
            }
            return true
        }

        fun putFields(vararg fields: String) {
            fields.forEach { field -> json.put(field, randomIdentityValue(field)) }
        }

        when (normalized) {
            "all" -> {
                copyTemplateIfAvailable(required = false)
                putFields(
                    "imei",
                    "imei2",
                    "imsi",
                    "imsi2",
                    "iccid",
                    "iccid2",
                    "meid",
                    "meid2",
                    "phone_number",
                    "phone_number2",
                    "serial",
                )
            }
            "template" -> if (!copyTemplateIfAvailable(required = true)) return null
            "sim1" -> putFields("imei", "imsi", "iccid", "meid", "phone_number")
            "sim2" -> putFields("imei2", "imsi2", "iccid2", "meid2", "phone_number2")
            "device" -> putFields("serial")
            "imei", "imei2", "imsi", "imsi2", "iccid", "iccid2", "meid", "meid2",
            "phone_number", "phone_number2", "serial" -> putFields(normalized)
            else -> throw IllegalArgumentException("Unsupported random identity field")
        }
        return json
    }

    private fun parseIdentityUpdates(json: String): Map<String, String?> {'''
web = replace_once(web, identity_tail, identity_helpers, "WebServer identity helper insertion")

random_route_pattern = re.compile(
    r'''        if \(uri == "/api/random_identity" && method == Method\.GET\) \{.*?\n        \}\n\n        if \(uri == "/api/auto_identity"''',
    re.S,
)
random_route_replacement = '''        if (uri == "/api/random_identity" && method == Method.GET) {
            val selection = getParam(session, "field")?.trim()?.lowercase().orEmpty().ifEmpty { "all" }
            return try {
                val json = randomIdentityJson(selection)
                    ?: return secureResponse(Response.Status.NOT_FOUND, "text/plain", "No templates found")
                secureResponse(Response.Status.OK, "application/json", json.toString())
            } catch (error: IllegalArgumentException) {
                secureResponse(Response.Status.BAD_REQUEST, "text/plain", error.message ?: "Invalid random identity request")
            }
        }

        if (uri == "/api/auto_identity"'''
web, count = random_route_pattern.subn(random_route_replacement, web, count=1)
if count != 1:
    raise RuntimeError(f"WebServer random route replacement: expected one anchor, found {count}")
write(web_rel, web)


# ---------------------------------------------------------------------------
# Service tests: single-field and bounded group randomization behavior.
# ---------------------------------------------------------------------------
test_rel = "service/src/test/java/cleveres/tricky/cleverestech/WebServerIdentityTest.kt"
test = read(test_rel)
test_anchor = '''    @Test
    fun `identity API refuses a symbolic link destination`() {'''
new_tests = '''    @Test
    fun `single random identity field returns only that validated value`() {
        val response = request("GET", "/api/random_identity?field=imei")
        assertEquals(200, response.first)
        val json = JSONObject(response.second)
        assertEquals(1, json.length())
        assertTrue(json.has("imei"))
        assertTrue(Config.isValidBuildVarEntry("ATTESTATION_ID_IMEI", json.getString("imei")))
    }

    @Test
    fun `random identity groups stay within their requested scope`() {
        val sim1Response = request("GET", "/api/random_identity?field=sim1")
        assertEquals(200, sim1Response.first)
        val sim1 = JSONObject(sim1Response.second)
        assertEquals(5, sim1.length())
        assertTrue(sim1.has("imei"))
        assertTrue(sim1.has("imsi"))
        assertTrue(sim1.has("iccid"))
        assertTrue(sim1.has("meid"))
        assertTrue(sim1.has("phone_number"))
        assertFalse(sim1.has("imei2"))
        assertFalse(sim1.has("serial"))

        val sim2Response = request("GET", "/api/random_identity?field=sim2")
        assertEquals(200, sim2Response.first)
        val sim2 = JSONObject(sim2Response.second)
        assertEquals(5, sim2.length())
        assertTrue(sim2.has("imei2"))
        assertTrue(sim2.has("imsi2"))
        assertTrue(sim2.has("iccid2"))
        assertTrue(sim2.has("meid2"))
        assertTrue(sim2.has("phone_number2"))
        assertFalse(sim2.has("imei"))
        assertFalse(sim2.has("serial"))

        val deviceResponse = request("GET", "/api/random_identity?field=device")
        assertEquals(200, deviceResponse.first)
        val device = JSONObject(deviceResponse.second)
        assertEquals(1, device.length())
        assertTrue(Config.isValidBuildVarEntry("ATTESTATION_ID_SERIAL", device.getString("serial")))
    }

    @Test
    fun `random template returns a bounded known template view`() {
        val response = request("GET", "/api/random_identity?field=template")
        assertEquals(200, response.first)
        val json = JSONObject(response.second)
        assertEquals(5, json.length())
        assertTrue(json.getString("id").isNotBlank())
        assertTrue(json.getString("model").isNotBlank())
        assertTrue(json.getString("manufacturer").isNotBlank())
        assertTrue(json.getString("fingerprint").isNotBlank())
        assertTrue(json.getString("securityPatch").isNotBlank())
    }

    @Test
    fun `unsupported random identity field is rejected`() {
        val response = request("GET", "/api/random_identity?field=unknown")
        assertEquals(400, response.first)
    }

    @Test
    fun `identity API refuses a symbolic link destination`() {'''
test = replace_once(test, test_anchor, new_tests, "WebServerIdentityTest random tests")
write(test_rel, test)


# ---------------------------------------------------------------------------
# WebUI static Identity form: per-value Random controls + Random All.
# ---------------------------------------------------------------------------
index_rel = "module/template/webroot/index.html"
index = read(index_rel)

css_anchor = '''        .identity-actions { margin-top: 18px; display: flex; justify-content: flex-end; gap: 10px; }
        .scope-note { font-size: 0.85em; color: #999; line-height: 1.5; margin-bottom: 15px; }'''
css_replacement = '''        .identity-actions { margin-top: 18px; display: flex; justify-content: flex-end; gap: 10px; }
        .identity-input-action { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: start; }
        .identity-input-slot { min-width: 0; }
        .identity-input-slot input, .identity-input-slot select { width: 100%; }
        .identity-random-btn { min-width: 84px; padding: 10px 12px; text-transform: none; letter-spacing: 0; }
        .scope-note { font-size: 0.85em; color: #999; line-height: 1.5; margin-bottom: 15px; }'''
index = replace_once(index, css_anchor, css_replacement, "Identity random button CSS")

template_select = '''<select id="templateSelect" onchange="previewTemplate()" style="margin-bottom:15px;"><option value="">No attestation template</option></select>'''
template_select_new = '''<div class="identity-input-action" style="margin-bottom:15px;"><div class="identity-input-slot"><select id="templateSelect" onchange="previewTemplate()"><option value="">No attestation template</option></select></div><button type="button" class="identity-random-btn" title="Random" aria-label="Random" onclick="runWithState(this, 'Generating...', () => generateRandomIdentity('template'))">Random</button></div>'''
index = replace_once(index, template_select, template_select_new, "Template Random control")

fields = [
    ("inputImei", "imei"),
    ("inputMeid", "meid"),
    ("inputImsi", "imsi"),
    ("inputIccid", "iccid"),
    ("inputPhoneNumber", "phone_number"),
    ("inputImei2", "imei2"),
    ("inputMeid2", "meid2"),
    ("inputImsi2", "imsi2"),
    ("inputIccid2", "iccid2"),
    ("inputPhoneNumber2", "phone_number2"),
    ("inputSerial", "serial"),
]
for input_id, field in fields:
    pattern = re.compile(
        rf'''(<div><label for="{re.escape(input_id)}">.*?</label>)(<input[^>]*\bid="{re.escape(input_id)}"[^>]*>)(</div>)'''
    )
    replacement = (
        r'''\1<div class="identity-input-action"><div class="identity-input-slot">\2</div>'''
        + f'''<button type="button" class="identity-random-btn" title="Random" aria-label="Random" onclick="runWithState(this, 'Generating...', () => randomizeIdentityField('{field}'))">Random</button></div>'''
        + r'''\3'''
    )
    index, count = pattern.subn(replacement, index, count=1)
    if count != 1:
        raise RuntimeError(f"Identity input Random control {input_id}: expected one anchor, found {count}")

index = index.replace("generateRandomIdentity)\" class=\"primary\"", "() => generateRandomIdentity('all'))\" class=\"primary\"", 1)
index = index.replace("generateRandomIdentity)\">Randomize All</button>", "() => generateRandomIdentity('all'))\">Randomize All</button>", 1)
if "runWithState(this, 'Generating...', generateRandomIdentity)" in index:
    raise RuntimeError("Legacy unscoped Randomize All handler remains")

random_function_pattern = re.compile(
    r'''        async function generateRandomIdentity\(\) \{.*?\n        \}\n\n        async function applyAutoIdentity\(\) \{''',
    re.S,
)
random_function_new = '''        const identityRandomInputMap = Object.freeze({
imei: 'inputImei', imei2: 'inputImei2', meid: 'inputMeid', meid2: 'inputMeid2',
imsi: 'inputImsi', imsi2: 'inputImsi2', iccid: 'inputIccid', iccid2: 'inputIccid2',
phone_number: 'inputPhoneNumber', phone_number2: 'inputPhoneNumber2', serial: 'inputSerial'
        });

        async function fetchRandomIdentity(selection = 'all') {
const res = await fetchAuth('/api/random_identity?field=' + encodeURIComponent(selection));
if (!res.ok) throw new Error(await res.text());
return res.json();
        }

        function applyRandomIdentityPayload(payload) {
Object.entries(identityRandomInputMap).forEach(([key, id]) => {
    if (!Object.prototype.hasOwnProperty.call(payload, key)) return;
    const input = document.getElementById(id);
    if (!input) return;
    input.value = payload[key] || '';
    input.dispatchEvent(new Event('input', { bubbles: true }));
});
if (payload.id) {
    const sel = document.getElementById('templateSelect');
    if (sel && [...sel.options].some(option => option.value === payload.id)) {
        sel.value = payload.id;
        sel.dataset.lockExtras = 'true';
        previewTemplate();
    }
}
        }

        async function randomizeIdentityField(field) {
const payload = await fetchRandomIdentity(field);
applyRandomIdentityPayload(payload);
notify('Identity value randomized');
        }

        async function generateRandomIdentity(selection = 'all') {
try {
    const payload = await fetchRandomIdentity(selection);
    applyRandomIdentityPayload(payload);
    notify(selection === 'all' ? 'Identity Generated' : 'Identity value randomized');
} catch (e) {
    console.error(e);
    notify('Error generating identity: ' + e.message, 'error');
}
        }

        async function applyAutoIdentity() {'''
index, count = random_function_pattern.subn(random_function_new, index, count=1)
if count != 1:
    raise RuntimeError(f"Identity random JS refactor: expected one anchor, found {count}")
write(index_rel, index)


# ---------------------------------------------------------------------------
# policy.js: Identity master/children live in the Identity page, not Dashboard.
# ---------------------------------------------------------------------------
policy_rel = "module/template/webroot/policy.js"
policy = read(policy_rel)

policy = replace_once(policy, "  const identityOn = identityEnabled();\n", "", "Remove dashboard identity state")
identity_children_line = '''  const identityChildren = `<div class="ct-subcontrols" id="${prefix}_identity_children" ${identityOn ? '' : 'hidden'}>${FEATURE_KEYS.map(([key,title,desc]) => `<div class="row"><label for="${prefix}_${key}" style="flex:1;padding-right:10px"><strong>${escapeHtml(title)}</strong><span class="res-desc">${escapeHtml(desc)}</span></label>${switchMarkup(`${prefix}_${key}`,Boolean(features && features[key]),`data-policy-feature="${key}"`)}</div>`).join('')}</div>`;
'''
policy = replace_once(policy, identity_children_line, "", "Remove dashboard identity children")
identity_card_line = '''    ${cardMarkup(`${prefix}_identity`,'Identity','Optional identity substitution. Turn it on first, then choose only the child identity paths you want.',identityOn,identityChildren + helpMarkup('Identity is optional. Core Keystore/TEE protection is independent from this switch.'))}
'''
policy = replace_once(policy, identity_card_line, "", "Remove dashboard Identity card")
policy = replace_once(policy, "  const identityToggle = panel.querySelector(`#${prefix}_identity`);\n", "", "Remove dashboard identity toggle binding")

identity_bind_block = '''  if (identityToggle) identityToggle.onchange = () => {
    const enabled = identityToggle.checked;
    savePolicy(next => FEATURE_KEYS.forEach(([key]) => { next.features[key] = enabled; }), enabled ? 'Identity enabled' : 'Identity disabled');
  };
  panel.querySelectorAll('[data-policy-feature]').forEach(toggle => {
    toggle.onchange = () => {
      const key = toggle.dataset.policyFeature;
      savePolicy(next => { next.features[key] = toggle.checked; }, `${toggle.closest('.row').querySelector('strong').textContent} updated`);
    };
  });
'''
policy = replace_once(policy, identity_bind_block, "", "Move Identity feature bindings")

legacy_cleanup = '''      if (/^Identity Controls$/i.test(title)) panel.remove();'''
legacy_cleanup_new = '''      if (/^Identity Controls$/i.test(title) && panel.id !== 'ct_identity_controls') panel.remove();'''
policy = replace_once(policy, legacy_cleanup, legacy_cleanup_new, "Preserve policy-owned Identity Controls")

feature_center_end = '''  }
}

function installIdentityBanner() {'''
identity_controls = '''  }
}

function identityControlsMarkup(prefix) {
  const features = policyState ? policyState.features : {};
  const identityOn = identityEnabled();
  const children = FEATURE_KEYS.map(([key,title,desc]) => `<div class="row"><label for="${prefix}_${key}" style="flex:1;padding-right:10px"><strong>${escapeHtml(title)}</strong><span class="res-desc">${escapeHtml(desc)}</span></label>${switchMarkup(`${prefix}_${key}`,Boolean(features && features[key]),`data-policy-feature="${key}"`)}</div>`).join('');
  return `<div class="ct-feature-card"><div class="row"><label for="${prefix}_master" style="flex:1;min-width:0;padding-right:12px"><strong>Identity</strong><span class="res-desc">Optional identity substitution. Enable only the paths you need.</span></label>${switchMarkup(`${prefix}_master`,identityOn)}</div><div class="ct-subcontrols" id="${prefix}_children" ${identityOn ? '' : 'hidden'}>${children}</div>${helpMarkup('Identity is optional. Core Keystore/TEE protection is independent from this switch.')}</div>`;
}

function bindIdentityControls(panel, prefix) {
  const master = panel.querySelector(`#${prefix}_master`);
  const children = panel.querySelector(`#${prefix}_children`);
  if (master) master.onchange = () => {
    const enabled = master.checked;
    if (children) children.hidden = !enabled;
    savePolicy(next => FEATURE_KEYS.forEach(([key]) => { next.features[key] = enabled; }), enabled ? 'Identity enabled' : 'Identity disabled');
  };
  panel.querySelectorAll('[data-policy-feature]').forEach(toggle => {
    toggle.onchange = () => {
      const key = toggle.dataset.policyFeature;
      savePolicy(next => { next.features[key] = toggle.checked; }, `${toggle.closest('.row').querySelector('strong').textContent} updated`);
    };
  });
}

function installIdentityControls() {
  const spoof = document.getElementById('spoof');
  if (!spoof || document.getElementById('ct_identity_controls')) return;
  const panel = document.createElement('div');
  panel.id = 'ct_identity_controls';
  panel.className = 'panel';
  panel.innerHTML = '<h3>Identity Controls</h3><div class="scope-note">Enable only the identity paths you need. Disabled paths do not start optional interceptors.</div><div class="ct-control-host"></div>';
  spoof.prepend(panel);
}

function renderIdentityControls() {
  if (!policyState) return;
  installIdentityControls();
  const panel = document.getElementById('ct_identity_controls');
  const host = panel && panel.querySelector('.ct-control-host');
  if (!panel || !host) return;
  host.innerHTML = identityControlsMarkup('ct_identity');
  bindIdentityControls(panel,'ct_identity');
}

function installIdentityBanner() {'''
policy = replace_once(policy, feature_center_end, identity_controls, "Insert Identity controls owner")

banner_old = '''    banner.innerHTML = 'Identity is currently disabled. You can enable it from Dashboard. <button type="button" style="margin-left:8px;padding:8px 10px;min-height:38px">Dashboard</button>';
    banner.querySelector('button').onclick = () => global.switchTab && global.switchTab('dashboard');
    spoof.prepend(banner);'''
banner_new = '''    banner.textContent = 'Identity is currently disabled. Enable only the identity paths you need below.';
    spoof.prepend(banner);'''
policy = replace_once(policy, banner_old, banner_new, "Identity disabled banner")

policy = replace_once(
    policy,
    '''  renderFeatureCenter();
  installIdentityBanner();''',
    '''  renderFeatureCenter();
  renderIdentityControls();
  installIdentityBanner();''',
    "Render Identity controls",
)
policy = replace_once(
    policy,
    '''  installFeatureCenter();
  installConfigurationActions();''',
    '''  installFeatureCenter();
  installIdentityControls();
  installConfigurationActions();''',
    "Install Identity controls",
)
write(policy_rel, policy)


# ---------------------------------------------------------------------------
# Locales: every new first-party string is translated in all built-in locales.
# ---------------------------------------------------------------------------
ux_rel = "module/template/webroot/ux.js"
ux = read(ux_rel)
new_strings = {
    "tr": {
        "Identity Controls": "Kimlik Denetimleri",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "Yalnızca ihtiyacınız olan kimlik yollarını etkinleştirin. Devre dışı yollar isteğe bağlı yakalayıcıları başlatmaz.",
        "Identity is currently disabled. Enable only the identity paths you need below.": "Kimlik şu anda devre dışı. Aşağıdan yalnızca ihtiyacınız olan kimlik yollarını etkinleştirin.",
        "Random": "Rastgele",
        "Identity value randomized": "Kimlik değeri rastgeleleştirildi",
    },
    "zh-CN": {
        "Identity Controls": "身份控制",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "只启用你需要的身份路径。禁用的路径不会启动可选拦截器。",
        "Identity is currently disabled. Enable only the identity paths you need below.": "身份功能当前已关闭。请在下方仅启用你需要的身份路径。",
        "Random": "随机",
        "Identity value randomized": "身份值已随机化",
    },
    "es": {
        "Identity Controls": "Controles de identidad",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "Activa solo las rutas de identidad que necesites. Las rutas desactivadas no inician interceptores opcionales.",
        "Identity is currently disabled. Enable only the identity paths you need below.": "La identidad está desactivada. Activa abajo solo las rutas que necesites.",
        "Random": "Aleatorio",
        "Identity value randomized": "Valor de identidad aleatorizado",
    },
    "de": {
        "Identity Controls": "Identitätssteuerung",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "Aktiviere nur die benötigten Identitätspfade. Deaktivierte Pfade starten keine optionalen Interzeptoren.",
        "Identity is currently disabled. Enable only the identity paths you need below.": "Identität ist derzeit deaktiviert. Aktiviere unten nur die benötigten Identitätspfade.",
        "Random": "Zufällig",
        "Identity value randomized": "Identitätswert randomisiert",
    },
    "ru": {
        "Identity Controls": "Управление идентичностью",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "Включайте только нужные пути идентичности. Отключённые пути не запускают необязательные перехватчики.",
        "Identity is currently disabled. Enable only the identity paths you need below.": "Идентичность сейчас отключена. Ниже включите только нужные пути.",
        "Random": "Случайно",
        "Identity value randomized": "Значение идентичности рандомизировано",
    },
    "id": {
        "Identity Controls": "Kontrol Identitas",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "Aktifkan hanya jalur identitas yang diperlukan. Jalur yang dinonaktifkan tidak menjalankan interceptor opsional.",
        "Identity is currently disabled. Enable only the identity paths you need below.": "Identitas saat ini dinonaktifkan. Aktifkan hanya jalur yang diperlukan di bawah.",
        "Random": "Acak",
        "Identity value randomized": "Nilai identitas diacak",
    },
    "hi": {
        "Identity Controls": "पहचान नियंत्रण",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "केवल आवश्यक पहचान पथ सक्षम करें। अक्षम पथ वैकल्पिक इंटरसेप्टर शुरू नहीं करते।",
        "Identity is currently disabled. Enable only the identity paths you need below.": "पहचान अभी अक्षम है। नीचे केवल आवश्यक पहचान पथ सक्षम करें।",
        "Random": "रैंडम",
        "Identity value randomized": "पहचान मान रैंडम किया गया",
    },
    "ar": {
        "Identity Controls": "عناصر التحكم بالهوية",
        "Enable only the identity paths you need. Disabled paths do not start optional interceptors.": "فعّل فقط مسارات الهوية التي تحتاجها. المسارات المعطلة لا تشغّل المعترضات الاختيارية.",
        "Identity is currently disabled. Enable only the identity paths you need below.": "الهوية معطلة حاليًا. فعّل أدناه فقط مسارات الهوية التي تحتاجها.",
        "Random": "عشوائي",
        "Identity value randomized": "تم توليد قيمة هوية عشوائية",
    },
}
locale_markers = {
    "tr": "        tr: {\n",
    "zh-CN": "        'zh-CN': {\n",
    "es": "        es: {\n",
    "de": "        de: {\n",
    "ru": "        ru: {\n",
    "id": "        id: {\n",
    "hi": "        hi: {\n",
    "ar": "        ar: {\n",
}
for locale, values in new_strings.items():
    marker = locale_markers[locale]
    if ux.count(marker) != 1:
        raise RuntimeError(f"Locale marker {locale}: expected one anchor")
    line = "            " + ", ".join(
        f"{source!r}: {translation!r}" for source, translation in values.items()
    ) + ",\n"
    ux = ux.replace(marker, marker + line, 1)
write(ux_rel, ux)


# ---------------------------------------------------------------------------
# Localization regression coverage for the new complete surface.
# ---------------------------------------------------------------------------
loc_test_rel = "module/webui-tests/localization.test.js"
loc_test = read(loc_test_rel)
loc_anchor = '''    'Feature Center',
    'What does this do?',
'''
loc_new = '''    'Feature Center',
    'Identity Controls',
    'Enable only the identity paths you need. Disabled paths do not start optional interceptors.',
    'Identity is currently disabled. Enable only the identity paths you need below.',
    'Random',
    'Identity value randomized',
    'What does this do?',
'''
loc_test = replace_once(loc_test, loc_anchor, loc_new, "Localization complete Identity surface")
write(loc_test_rel, loc_test)


# ---------------------------------------------------------------------------
# WebUI structural regression: all value controls get on-demand Random buttons;
# no periodic/background randomization is introduced.
# ---------------------------------------------------------------------------
ui_test_rel = "module/webui-tests/identity-randomization.test.js"
ui_test = r'''const assert = require('assert');
const fs = require('fs');

const index = fs.readFileSync('module/template/webroot/index.html', 'utf8');
const policy = fs.readFileSync('module/template/webroot/policy.js', 'utf8');

const fields = [
  ['imei','inputImei'], ['meid','inputMeid'], ['imsi','inputImsi'], ['iccid','inputIccid'], ['phone_number','inputPhoneNumber'],
  ['imei2','inputImei2'], ['meid2','inputMeid2'], ['imsi2','inputImsi2'], ['iccid2','inputIccid2'], ['phone_number2','inputPhoneNumber2'],
  ['serial','inputSerial']
];
for (const [field,id] of fields) {
  assert.ok(index.includes(`id="${id}"`), `missing identity input ${id}`);
  assert.ok(index.includes(`randomizeIdentityField('${field}')`), `missing per-value Random action for ${field}`);
}
assert.ok(index.includes("generateRandomIdentity('template')"), 'template Random action is missing');
assert.ok(index.includes("generateRandomIdentity('all')"), 'Randomize All action is missing');
assert.ok(index.includes("/api/random_identity?field="), 'randomization must use the bounded on-demand API');
assert.ok(!/setInterval\s*\([^)]*random/i.test(index), 'randomization must not create a periodic worker');

const featureStart = policy.indexOf('function buildFeatureCenterMarkup(prefix)');
const featureEnd = policy.indexOf('function renderFeatureCenter()', featureStart);
assert.ok(featureStart >= 0 && featureEnd > featureStart, 'Feature Center function is missing');
const featureCenter = policy.slice(featureStart, featureEnd);
assert.ok(!featureCenter.includes("cardMarkup(`${prefix}_identity`"), 'Identity master must not remain on Dashboard');
assert.ok(policy.includes("panel.id = 'ct_identity_controls'"), 'Identity Controls panel is missing');
assert.ok(policy.includes('function renderIdentityControls()'), 'Identity Controls renderer is missing');
assert.ok(policy.includes("panel.id !== 'ct_identity_controls'"), 'legacy cleanup must preserve policy-owned Identity Controls');

console.log('Identity randomization and placement regression tests passed');
'''
ui_path = ROOT / ui_test_rel
if ui_path.exists():
    raise RuntimeError(f"{ui_test_rel} already exists")
ui_path.write_text(ui_test, encoding="utf-8")

print("Identity randomization patch applied successfully")
