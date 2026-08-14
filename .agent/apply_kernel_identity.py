from pathlib import Path

# ---- service-side validated configuration ----
manager = Path('service/src/main/java/cleveres/tricky/cleverestech/KernelIdentityManager.kt')
manager.write_text(r'''package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.SecureFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

object KernelIdentityManager {
    data class Preset(val id: String, val label: String, val release: String, val version: String)
    data class Config(val enabled: Boolean, val preset: String, val release: String, val version: String)

    private const val FILE_NAME = "kernel_identity.json"
    private const val MAX_BYTES = 4096L
    private const val MAX_UTS_FIELD = 64
    private val presets = listOf(
        Preset("android14-5.15", "Android 14 GKI 5.15", "5.15.208-android14", "#1 SMP PREEMPT_DYNAMIC"),
        Preset("android14-6.1", "Android 14 GKI 6.1", "6.1.172-android14", "#1 SMP PREEMPT_DYNAMIC"),
        Preset("android15-6.6", "Android 15 GKI 6.6", "6.6.139-android15", "#1 SMP PREEMPT_DYNAMIC"),
        Preset("android16-6.12", "Android 16 GKI 6.12", "6.12.81-android16", "#1 SMP PREEMPT_DYNAMIC"),
    )
    private val presetIds = presets.mapTo(HashSet()) { it.id }

    @Volatile private var file: File? = null
    @Volatile private var state = Config(false, "android15-6.6", presets[2].release, presets[2].version)

    @Synchronized
    fun initialize(configDir: File) {
        file = File(configDir, FILE_NAME)
        state = readValidated(file!!)
    }

    fun current(): Config = state

    fun activationPayload(): String {
        val current = state
        return if (current.enabled) "1|${current.release}|${current.version}" else "0||"
    }

    fun json(): JSONObject {
        val current = state
        val catalog = JSONArray()
        presets.forEach { preset ->
            catalog.put(JSONObject().put("id", preset.id).put("label", preset.label).put("release", preset.release).put("version", preset.version))
        }
        return JSONObject()
            .put("enabled", current.enabled)
            .put("preset", current.preset)
            .put("release", current.release)
            .put("version", current.version)
            .put("presets", catalog)
    }

    @Synchronized
    fun save(json: String): Config {
        val obj = JSONObject(json)
        require(obj.length() in 1..4) { "Invalid kernel identity request" }
        val enabled = obj.optBoolean("enabled", false)
        val preset = obj.optString("preset", "custom").trim()
        require(preset == "custom" || preset in presetIds) { "Invalid GKI preset" }
        val selected = presets.firstOrNull { it.id == preset }
        val release = obj.optString("release", selected?.release ?: "").trim()
        val version = obj.optString("version", selected?.version ?: "").trim()
        require(isValidField(release) && isValidField(version)) { "Invalid kernel identity field" }
        val next = Config(enabled, preset, release, version)
        val target = requireNotNull(file) { "Kernel identity manager is not initialized" }
        val stored = JSONObject().put("enabled", enabled).put("preset", preset).put("release", release).put("version", version).toString(2)
        require(stored.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Kernel identity configuration is too large" }
        SecureFile.writeText(target, stored)
        state = next
        return next
    }

    private fun readValidated(target: File): Config {
        if (!Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) return state
        if (!Files.isRegularFile(target.toPath(), LinkOption.NOFOLLOW_LINKS) || target.length() !in 1..MAX_BYTES) {
            Logger.w("Ignoring invalid kernel identity configuration file")
            return state.copy(enabled = false)
        }
        return runCatching {
            val obj = JSONObject(target.readText(Charsets.UTF_8))
            val preset = obj.optString("preset", "custom").trim()
            require(preset == "custom" || preset in presetIds)
            val selected = presets.firstOrNull { it.id == preset }
            val release = obj.optString("release", selected?.release ?: "").trim()
            val version = obj.optString("version", selected?.version ?: "").trim()
            require(isValidField(release) && isValidField(version))
            Config(obj.optBoolean("enabled", false), preset, release, version)
        }.getOrElse {
            Logger.w("Ignoring malformed kernel identity configuration")
            state.copy(enabled = false)
        }
    }

    private fun isValidField(value: String): Boolean {
        if (value.isEmpty() || value.length > MAX_UTS_FIELD) return false
        return value.none { it.isISOControl() || it == '|' } && value.all {
            it.isLetterOrDigit() || it in " ._+-/#():=@"
        }
    }
}
''')

# Initialize manager at service startup.
main = Path('service/src/main/java/cleveres/tricky/cleverestech/Main.kt')
s = main.read_text()
needle = '            Config.initialize()\n            BootLogic.run()'
if needle not in s: raise SystemExit('Main init marker missing')
s = s.replace(needle, '            KernelIdentityManager.initialize(configDir)\n            Config.initialize()\n            BootLogic.run()', 1)
main.write_text(s)

# Refactor injector activation so runtime kernel config is passed safely as one validated argument.
ks = Path('service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt')
s = ks.read_text()
insert = '''    private fun runNativeActivation(pid: Int, symbol: String): Boolean {
        return try {
            val modulePath = getModuleDir()
            val injectPath = "$modulePath/inject"
            val process =
                ProcessBuilder(
                    injectPath,
                    pid.toString(),
                    "$modulePath/libcleverestricky.so",
                    symbol,
                    KernelIdentityManager.activationPayload(),
                ).redirectOutput(java.io.File("/dev/null"))
                    .redirectError(java.io.File("/dev/null"))
                    .start()
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                Logger.e("native activation timed out after 30s, killing it")
                process.destroyForcibly()
                false
            } else {
                val exitCode = process.exitValue()
                if (exitCode != 0) Logger.e("native activation failed (exit=$exitCode)")
                exitCode == 0
            }
        } catch (error: Exception) {
            Logger.e("failed to run native activation", error)
            false
        }
    }

    @Synchronized
    fun refreshKernelIdentity(): Boolean {
        val pid = findKeystore2Pid() ?: return false
        if (!injected || injectedPid != pid) return true
        return runNativeActivation(pid, "resume")
    }

'''
marker = '    @Synchronized\n    fun tryRunKeystoreInterceptor(): Boolean {'
if insert not in s:
    if marker not in s: raise SystemExit('Keystore insertion marker missing')
    s = s.replace(marker, insert + marker, 1)
old = '''            try {
                val modulePath = getModuleDir()
                val injectPath = "$modulePath/inject"
                val p =
                    ProcessBuilder(
                        injectPath,
                        pid.toString(),
                        "$modulePath/libcleverestricky.so",
                        symbol,
                    ).redirectOutput(java.io.File("/dev/null"))
                        .redirectError(java.io.File("/dev/null"))
                        .start()
                val completed = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)
                if (!completed) {
                    Logger.e("inject process timed out after 30s, killing it")
                    p.destroyForcibly()
                    triedCount.incrementAndGet()
                    return false
                }
                val exitCode = p.exitValue()
                if (exitCode != 0) {
                    Logger.e("failed to activate the keystore Binder hook (exit=$exitCode)!")
                    triedCount.incrementAndGet()
                    return false
                } else {
                    Logger.i("keystore Binder hook activated successfully")
                    injected = true
                    injectedPid = pid
                }
                triedCount.incrementAndGet()
                return false
            } catch (error: Exception) {
                triedCount.incrementAndGet()
                Logger.e("failed to run the keystore injector", error)
                return false
            }
'''
new = '''            if (!runNativeActivation(pid, symbol)) {
                triedCount.incrementAndGet()
                return false
            }
            Logger.i("keystore Binder hook activated successfully")
            injected = true
            injectedPid = pid
            triedCount.incrementAndGet()
            return false
'''
if old not in s: raise SystemExit('Keystore activation block missing')
s = s.replace(old, new, 1)
ks.write_text(s)

# Web API: bounded validated state, immediate native reconfigure where keystore2 is already injected.
web = Path('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt')
s = web.read_text()
route_marker = '        if (uri == "/api/templates" && method == Method.GET) {'
routes = '''        if (uri == "/api/kernel_identity" && method == Method.GET) {
            return secureResponse(Response.Status.OK, "application/json", KernelIdentityManager.json().toString())
        }

        if (uri == "/api/kernel_identity" && method == Method.POST) {
            val body = HashMap<String, String>()
            return try {
                session.parseBody(body)
                val data = getParam(session, "data") ?: throw IllegalArgumentException("Missing kernel identity data")
                require(data.toByteArray(Charsets.UTF_8).size <= 4096) { "Kernel identity request is too large" }
                KernelIdentityManager.save(data)
                val applied = KeystoreInterceptor.refreshKernelIdentity()
                secureResponse(Response.Status.OK, "application/json", KernelIdentityManager.json().put("applied", applied).toString())
            } catch (error: IllegalArgumentException) {
                secureResponse(Response.Status.BAD_REQUEST, "text/plain", error.message ?: "Invalid kernel identity data")
            } catch (error: Exception) {
                Logger.e("Failed to save kernel identity configuration", error)
                secureResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Kernel identity configuration was not saved")
            }
        }

'''
if '/api/kernel_identity' not in s:
    if route_marker not in s: raise SystemExit('WebServer route marker missing')
    s = s.replace(route_marker, routes + route_marker, 1)
web.write_text(s)

# Rust injector: optional activation context. Existing 4-argument callers remain valid.
engine = Path('rust/injector-core/src/engine.rs')
s = engine.read_text()
s = s.replace('''    if arguments.len() != 4 {
        return Err("expected a process, library path, and entry name".into());
    }
''', '''    if arguments.len() !in 4..=5 {
        return Err("expected a process, library path, entry name, and optional activation context".into());
    }
''', 1)
s = s.replace('''    let entry_value = arguments[3].as_os_str().as_bytes();
    let current_pid = unsafe { getpid() };
''', '''    let entry_value = arguments[3].as_os_str().as_bytes();
    let activation_value = arguments.get(4).map(|value| value.as_os_str().as_bytes());
    if let Some(value) = activation_value {
        if value.len() > 256 || value.contains(&0) || value.iter().any(|byte| *byte < 0x20 || *byte > 0x7e) {
            return Err("invalid activation context".into());
        }
    }
    let activation_c_string = activation_value
        .map(CString::new)
        .transpose()
        .map_err(|_| "activation context contains a null byte".to_string())?;
    let current_pid = unsafe { getpid() };
''', 1)
s = s.replace('''    inject_library(pid, &library, &canonical_c_string, &entry_c_string)
}''', '''    inject_library(
        pid,
        &library,
        &canonical_c_string,
        &entry_c_string,
        activation_c_string.as_deref(),
    )
}''', 1)
s = s.replace('''fn inject_library(
    pid: i32,
    library: &File,
    library_path: &CStr,
    entry_name: &CStr,
) -> EngineResult<()> {''', '''fn inject_library(
    pid: i32,
    library: &File,
    library_path: &CStr,
    entry_name: &CStr,
    activation_context: Option<&CStr>,
) -> EngineResult<()> {''', 1)
s = s.replace('''        let entry_result = session.call(remote_entry, symbols.libc_return, &[remote_handle])?;
''', '''        let remote_activation_context = match activation_context {
            Some(context) => session.push_c_string(context)?,
            None => 0,
        };
        let entry_result = session.call(remote_entry, symbols.libc_return, &[remote_activation_context])?;
''', 1)
engine.write_text(s)

# Native uname hook. It uses raw syscall for the genuine base result, so it cannot recurse.
Path('module/src/main/cpp/kernel_identity.hpp').write_text(r'''#pragma once

namespace cleverestricky::kernel_identity {
void configure(const char *payload);
bool install_hooks_if_enabled();
}
''')
Path('module/src/main/cpp/kernel_identity.cpp').write_text(r'''#include "kernel_identity.hpp"

#include <sys/syscall.h>
#include <sys/utsname.h>
#include <unistd.h>

#include <atomic>
#include <cstring>
#include <mutex>
#include <set>
#include <string>
#include <utility>

#include "lsplt.hpp"

namespace cleverestricky::kernel_identity {
namespace {
std::mutex g_config_mutex;
std::atomic<bool> g_enabled{false};
std::atomic<bool> g_hooks_installed{false};
std::string g_release;
std::string g_version;

bool valid_field(const std::string &value, size_t capacity) {
  if (value.empty() || value.size() >= capacity) return false;
  for (const unsigned char ch : value) {
    if (ch < 0x20 || ch > 0x7e || ch == '|') return false;
  }
  return true;
}

void copy_field(char *destination, size_t capacity, const std::string &value) {
  std::memset(destination, 0, capacity);
  std::memcpy(destination, value.data(), value.size());
}

int hooked_uname(struct utsname *buffer) {
  const int result = static_cast<int>(syscall(SYS_uname, buffer));
  if (result != 0 || buffer == nullptr || !g_enabled.load(std::memory_order_acquire)) return result;
  std::lock_guard<std::mutex> guard(g_config_mutex);
  if (!g_enabled.load(std::memory_order_relaxed)) return result;
  copy_field(buffer->release, sizeof(buffer->release), g_release);
  copy_field(buffer->version, sizeof(buffer->version), g_version);
  return result;
}

bool candidate_path(const std::string &path) {
  if (path.empty() || path[0] == '[' || path.find("libcleverestricky.so") != std::string::npos) return false;
  if (path.ends_with("/keystore2")) return true;
  return path.ends_with(".so") &&
         (path.starts_with("/system/") || path.starts_with("/apex/") || path.starts_with("/vendor/") ||
          path.starts_with("/product/") || path.starts_with("/system_ext/"));
}
}  // namespace

void configure(const char *payload) {
  bool enabled = false;
  std::string release;
  std::string version;
  if (payload != nullptr) {
    const std::string value(payload);
    const size_t first = value.find('|');
    const size_t second = first == std::string::npos ? std::string::npos : value.find('|', first + 1);
    if (first != std::string::npos && second != std::string::npos && value.substr(0, first) == "1") {
      release = value.substr(first + 1, second - first - 1);
      version = value.substr(second + 1);
      enabled = valid_field(release, sizeof(utsname{}.release)) && valid_field(version, sizeof(utsname{}.version));
    }
  }
  {
    std::lock_guard<std::mutex> guard(g_config_mutex);
    g_release = enabled ? release : std::string{};
    g_version = enabled ? version : std::string{};
    g_enabled.store(enabled, std::memory_order_release);
  }
}

bool install_hooks_if_enabled() {
  if (!g_enabled.load(std::memory_order_acquire)) return true;
  if (g_hooks_installed.load(std::memory_order_acquire)) return true;

  auto maps = lsplt::MapInfo::Scan();
  std::set<std::pair<dev_t, ino_t>> seen;
  size_t installed = 0;
  for (const auto &map : maps) {
    if (!candidate_path(map.path) || map.dev == 0 || map.inode == 0 || !seen.emplace(map.dev, map.inode).second) continue;
    void *backup = nullptr;
    if (!lsplt::RegisterHook(map.dev, map.inode, "uname", reinterpret_cast<void *>(hooked_uname), &backup)) continue;
    const bool committed = lsplt::CommitHook();
    if (committed && backup != nullptr) ++installed;
  }
  if (installed == 0) return false;
  g_hooks_installed.store(true, std::memory_order_release);
  return true;
}
}  // namespace cleverestricky::kernel_identity
''')

cmake = Path('module/src/main/cpp/CMakeLists.txt')
s = cmake.read_text()
s = s.replace('add_library(${MODULE_NAME} SHARED binder_interceptor.cpp)', 'add_library(${MODULE_NAME} SHARED binder_interceptor.cpp kernel_identity.cpp)', 1)
cmake.write_text(s)

binder = Path('module/src/main/cpp/binder_interceptor.cpp')
s = binder.read_text()
include_marker = '#include "lsplt.hpp"'
if '#include "kernel_identity.hpp"' not in s:
    if include_marker not in s: raise SystemExit('binder include marker missing')
    s = s.replace(include_marker, include_marker + '\n#include "kernel_identity.hpp"', 1)
old = '''extern "C" [[gnu::visibility("default")]] [[gnu::used]] bool
entry(void *) {
  LOGI("native Binder interceptor injected");
  return initialize_hooks();
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]] bool
resume(void *) {
  LOGI("resuming parked native Binder hook");
  return initialize_hooks();
}'''
new = '''extern "C" [[gnu::visibility("default")]] [[gnu::used]] bool
entry(void *activation_context) {
  LOGI("native Binder interceptor injected");
  cleverestricky::kernel_identity::configure(static_cast<const char *>(activation_context));
  const bool binder_ready = initialize_hooks();
  if (binder_ready && !cleverestricky::kernel_identity::install_hooks_if_enabled()) {
    LOGW("kernel identity hook requested but no uname import could be hooked; Binder core remains active");
  }
  return binder_ready;
}

extern "C" [[gnu::visibility("default")]] [[gnu::used]] bool
resume(void *activation_context) {
  LOGI("resuming parked native Binder hook");
  cleverestricky::kernel_identity::configure(static_cast<const char *>(activation_context));
  const bool binder_ready = initialize_hooks();
  if (binder_ready && !cleverestricky::kernel_identity::install_hooks_if_enabled()) {
    LOGW("kernel identity hook requested but no uname import could be hooked; Binder core remains active");
  }
  return binder_ready;
}'''
if old not in s: raise SystemExit('binder export block missing')
s = s.replace(old, new, 1)
binder.write_text(s)

# Compact WebUI: entire section is collapsed; inputs only show when enabled.
policy = Path('module/template/webroot/policy.js')
s = policy.read_text()
marker = 'function installAppsProfileCard() {'
feature = r'''
function installKernelIdentityControls() {
  const spoof = document.getElementById('spoof');
  if (!spoof || document.getElementById('ct_kernel_identity_panel')) return;
  const panel = document.createElement('div');
  panel.id = 'ct_kernel_identity_panel';
  panel.className = 'panel';
  panel.innerHTML = `<details><summary><strong>Kernel Identity</strong></summary><div class="scope-note" style="margin-top:12px">Optionally overrides uname release/version inside the injected Keystore runtime. Official GKI presets use published base kernel versions and remain editable.</div><div class="row"><label for="ct_kernel_enabled" style="flex:1"><strong>Hook kernel name</strong><span class="res-desc">Disabled by default. Core Binder protection is independent from this option.</span></label>${switchMarkup('ct_kernel_enabled',false)}</div><div id="ct_kernel_children" hidden><label for="ct_kernel_preset">GKI preset</label><select id="ct_kernel_preset"></select><div class="ct-choice-grid" style="margin-top:10px"><div><label for="ct_kernel_release">uname release</label><input id="ct_kernel_release" type="text" maxlength="64" autocomplete="off" spellcheck="false"></div><div><label for="ct_kernel_version">uname version</label><input id="ct_kernel_version" type="text" maxlength="64" autocomplete="off" spellcheck="false"></div></div><button id="ct_kernel_save" class="primary" type="button" style="width:100%;margin-top:12px">Save kernel identity</button></div></details>`;
  const customPanel = document.getElementById('ct_custom_template_panel');
  if (customPanel) customPanel.insertAdjacentElement('afterend',panel); else spoof.append(panel);
  loadKernelIdentity().catch(error => notify(error.message || 'Could not load kernel identity','error'));
}

async function loadKernelIdentity() {
  const state = await request('/api/kernel_identity');
  const enabled = document.getElementById('ct_kernel_enabled');
  const children = document.getElementById('ct_kernel_children');
  const preset = document.getElementById('ct_kernel_preset');
  const release = document.getElementById('ct_kernel_release');
  const version = document.getElementById('ct_kernel_version');
  if (!enabled || !children || !preset || !release || !version) return;
  preset.innerHTML = '<option value="custom">Custom</option>' + (state.presets || []).map(item => `<option value="${escapeHtml(item.id)}">${escapeHtml(item.label)}</option>`).join('');
  enabled.checked = Boolean(state.enabled);
  children.hidden = !enabled.checked;
  preset.value = state.preset || 'custom';
  release.value = state.release || '';
  version.value = state.version || '';
  enabled.onchange = () => { children.hidden = !enabled.checked; };
  preset.onchange = () => {
    const selected = (state.presets || []).find(item => item.id === preset.value);
    if (selected) { release.value = selected.release; version.value = selected.version; }
  };
  document.getElementById('ct_kernel_save').onclick = async () => {
    const payload = {enabled:enabled.checked,preset:preset.value,release:release.value.trim(),version:version.value.trim()};
    const body = new URLSearchParams(); body.set('data',JSON.stringify(payload));
    const result = await request('/api/kernel_identity',{method:'POST',body});
    notify(result.applied ? 'Kernel identity applied' : 'Kernel identity saved for next native activation');
  };
}

'''
if 'function installKernelIdentityControls() {' not in s:
    if marker not in s: raise SystemExit('policy kernel insert marker missing')
    s = s.replace(marker, feature + marker, 1)
init = '  installCustomTemplateBuilder();\n  installAppsProfileCard();'
if init in s:
    s = s.replace(init, '  installCustomTemplateBuilder();\n  installKernelIdentityControls();\n  installAppsProfileCard();', 1)
else:
    raise SystemExit('policy init custom marker missing')
policy.write_text(s)

# Locale catalog: concise strings used by the new UI in all built-in locales.
ux = Path('module/template/webroot/ux.js')
u = ux.read_text()
if "'Kernel Identity': 'Çekirdek Kimliği'" not in u:
    marker = "    // Complete catalogs share one source key per row to keep all built-in\n"
    tr = '''    Object.assign(TRANSLATIONS.tr, {
        'Kernel Identity': 'Çekirdek Kimliği', 'Hook kernel name': 'Çekirdek adını hookla', 'GKI preset': 'GKI ön ayarı',
        'uname release': 'uname sürümü', 'uname version': 'uname derleme bilgisi', 'Save kernel identity': 'Çekirdek kimliğini kaydet',
        'Custom': 'Özel', 'Kernel identity applied': 'Çekirdek kimliği uygulandı',
        'Kernel identity saved for next native activation': 'Çekirdek kimliği sonraki yerel etkinleştirme için kaydedildi',
        'Could not load kernel identity': 'Çekirdek kimliği yüklenemedi',
        'Optionally overrides uname release/version inside the injected Keystore runtime. Official GKI presets use published base kernel versions and remain editable.': 'Enjekte edilen Keystore çalışma zamanında uname sürüm/derleme bilgisini isteğe bağlı olarak değiştirir. Resmî GKI ön ayarları yayımlanan temel çekirdek sürümlerini kullanır ve düzenlenebilir.',
        'Disabled by default. Core Binder protection is independent from this option.': 'Varsayılan olarak kapalıdır. Temel Binder koruması bu seçenekten bağımsızdır.'
    });

'''
    if marker not in u: raise SystemExit('ux marker missing')
    u = u.replace(marker, tr + marker, 1)
    end = '    ];\n\n    for (const row of COMPLETE_CATALOG_ROWS) {'
    rows = '''        ["Kernel Identity", "内核身份", "Identidad del kernel", "Kernel-Identität", "Идентичность ядра", "Identitas Kernel", "कर्नेल पहचान", "هوية النواة"],
        ["Hook kernel name", "Hook 内核名称", "Interceptar nombre del kernel", "Kernel-Namen hooken", "Перехватывать имя ядра", "Hook nama kernel", "कर्नेल नाम हुक करें", "اعتراض اسم النواة"],
        ["GKI preset", "GKI 预设", "Preajuste GKI", "GKI-Voreinstellung", "Профиль GKI", "Preset GKI", "GKI प्रीसेट", "إعداد GKI مسبق"],
        ["uname release", "uname release", "release de uname", "uname release", "uname release", "uname release", "uname release", "uname release"],
        ["uname version", "uname version", "versión de uname", "uname version", "uname version", "uname version", "uname version", "uname version"],
        ["Save kernel identity", "保存内核身份", "Guardar identidad del kernel", "Kernel-Identität speichern", "Сохранить идентичность ядра", "Simpan identitas kernel", "कर्नेल पहचान सहेजें", "حفظ هوية النواة"],
        ["Kernel identity applied", "内核身份已应用", "Identidad del kernel aplicada", "Kernel-Identität angewendet", "Идентичность ядра применена", "Identitas kernel diterapkan", "कर्नेल पहचान लागू की गई", "تم تطبيق هوية النواة"],
        ["Kernel identity saved for next native activation", "内核身份已保存，将在下次原生激活时应用", "Identidad del kernel guardada para la próxima activación nativa", "Kernel-Identität für die nächste native Aktivierung gespeichert", "Идентичность ядра сохранена для следующей нативной активации", "Identitas kernel disimpan untuk aktivasi native berikutnya", "कर्नेल पहचान अगली नेटिव सक्रियता के लिए सहेजी गई", "تم حفظ هوية النواة للتفعيل الأصلي التالي"],
        ["Could not load kernel identity", "无法加载内核身份", "No se pudo cargar la identidad del kernel", "Kernel-Identität konnte nicht geladen werden", "Не удалось загрузить идентичность ядра", "Tidak dapat memuat identitas kernel", "कर्नेल पहचान लोड नहीं हो सकी", "تعذر تحميل هوية النواة"],
        ["Disabled by default. Core Binder protection is independent from this option.", "默认关闭。核心 Binder 保护独立于此选项。", "Desactivado de forma predeterminada. La protección Binder principal es independiente de esta opción.", "Standardmäßig deaktiviert. Der Binder-Kernschutz ist von dieser Option unabhängig.", "По умолчанию отключено. Основная защита Binder не зависит от этой опции.", "Dinonaktifkan secara default. Perlindungan inti Binder tidak bergantung pada opsi ini.", "डिफ़ॉल्ट रूप से बंद। मुख्य Binder सुरक्षा इस विकल्प से स्वतंत्र है।", "معطل افتراضيا. حماية Binder الأساسية مستقلة عن هذا الخيار."],
        ["Optionally overrides uname release/version inside the injected Keystore runtime. Official GKI presets use published base kernel versions and remain editable.", "可选地覆盖注入 Keystore 运行时中的 uname release/version。官方 GKI 预设使用已发布的基础内核版本并可编辑。", "Sustituye opcionalmente release/version de uname dentro del entorno Keystore inyectado. Los preajustes GKI oficiales usan versiones base publicadas y siguen siendo editables.", "Überschreibt optional uname release/version in der injizierten Keystore-Laufzeit. Offizielle GKI-Voreinstellungen verwenden veröffentlichte Basis-Kernelversionen und bleiben editierbar.", "При необходимости заменяет uname release/version во внедрённой среде Keystore. Официальные профили GKI используют опубликованные базовые версии ядра и остаются редактируемыми.", "Secara opsional mengganti uname release/version di runtime Keystore yang diinjeksi. Preset GKI resmi memakai versi kernel dasar yang dipublikasikan dan tetap dapat diedit.", "इंजेक्ट किए गए Keystore रनटाइम में uname release/version को वैकल्पिक रूप से बदलता है। आधिकारिक GKI प्रीसेट प्रकाशित बेस कर्नेल संस्करणों का उपयोग करते हैं और संपादन योग्य रहते हैं।", "يستبدل اختياريا uname release/version داخل بيئة Keystore المحقونة. تستخدم إعدادات GKI الرسمية إصدارات النواة الأساسية المنشورة وتظل قابلة للتحرير."],
'''
    if end not in u: raise SystemExit('ux catalog end missing')
    u = u.replace(end, rows + end, 1)
ux.write_text(u)

# Tests: contract + current published base version presets + core independence.
Path('module/webui-tests/kernel-identity.test.js').write_text(r'''const fs = require('fs');
const assert = require('assert');
const policy = fs.readFileSync('module/template/webroot/policy.js','utf8');
const manager = fs.readFileSync('service/src/main/java/cleveres/tricky/cleverestech/KernelIdentityManager.kt','utf8');
const binder = fs.readFileSync('module/src/main/cpp/binder_interceptor.cpp','utf8');
const native = fs.readFileSync('module/src/main/cpp/kernel_identity.cpp','utf8');
const engine = fs.readFileSync('rust/injector-core/src/engine.rs','utf8');
assert(policy.includes('<details><summary><strong>Kernel Identity</strong>'), 'kernel UI must stay collapsed');
assert(policy.includes('children.hidden = !enabled.checked'), 'kernel inputs must be conditional');
for (const version of ['5.15.208-android14','6.1.172-android14','6.6.139-android15','6.12.81-android16']) assert(manager.includes(version), `missing GKI base ${version}`);
assert(native.includes('syscall(SYS_uname, buffer)'), 'hook must obtain genuine uname through raw syscall');
assert(native.includes('g_enabled.load'), 'disabled hook must preserve genuine uname');
assert(binder.includes('Binder core remains active'), 'optional kernel hook must not disable Binder core');
assert(engine.includes('optional activation context'), 'injector must validate optional native context');
console.log('kernel-identity regression checks passed');
''')

# Changelog current release only.
changelog = Path('CHANGELOG.md')
c = changelog.read_text().rstrip() + '\n'
bullet = '- Added an opt-in, localized Kernel Identity uname hook with editable Android 14/15/16 GKI base-version presets while keeping core Binder protection independent.\n'
if bullet not in c: c += bullet
changelog.write_text(c)

Path('.agent/apply_kernel_identity.py').unlink(missing_ok=True)
Path('.github/workflows/apply-kernel-identity.yml').unlink(missing_ok=True)
