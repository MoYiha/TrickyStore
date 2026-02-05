package cleveres.tricky.cleverestech

import android.system.Os
import cleveres.tricky.cleverestech.keystore.CertHack
import cleveres.tricky.cleverestech.util.KeyboxVerifier
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class WebServer(
    port: Int,
    private val configDir: File = File("/data/adb/cleverestricky"),
    private val permissionSetter: (File, Int) -> Unit = { f, m ->
        try {
            Os.chmod(f.absolutePath, m)
        } catch (t: Throwable) {
            Logger.e("failed to set permissions for ${f.name}", t)
        }
    }
) : NanoHTTPD("127.0.0.1", port) {

    val token = UUID.randomUUID().toString()
    private val MAX_UPLOAD_SIZE = 5 * 1024 * 1024L // 5MB

    private fun readFile(filename: String): String {
        return try {
            File(configDir, filename).readText()
        } catch (e: Exception) {
            ""
        }
    }

    private fun saveFile(filename: String, content: String): Boolean {
        return try {
            val f = File(configDir, filename)
            f.writeText(content)
            // Ensure proper permissions (0600)
            permissionSetter(f, 384)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun fileExists(filename: String): Boolean {
        return File(configDir, filename).exists()
    }

    private fun isValidSetting(name: String): Boolean {
        return name in setOf("global_mode", "tee_broken_mode", "rkp_bypass", "auto_beta_fetch", "auto_keybox_check")
    }

    private fun toggleFile(filename: String, enable: Boolean): Boolean {
        if (!isValidSetting(filename)) return false
        val f = File(configDir, filename)
        return try {
            if (enable) {
                if (!f.exists()) {
                    f.createNewFile()
                    permissionSetter(f, 384) // 0600
                }
            } else {
                if (f.exists()) f.delete()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun fetchTelegramCount(): String {
        return try {
            val url = URL("https://t.me/cleverestech")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val regex = java.util.regex.Pattern.compile("tgme_page_extra\">([0-9 ]+) members")
                val matcher = regex.matcher(html)
                if (matcher.find()) {
                    matcher.group(1)?.trim() ?: "Unknown"
                } else {
                    "Unknown"
                }
            } else {
                "Error: ${conn.responseCode}"
            }
        } catch (e: Exception) {
            "Error"
        }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val params = session.parms

        // Security: Enforce max upload size to prevent DoS
        if (method == Method.POST || method == Method.PUT) {
             val lenStr = session.headers["content-length"]
             if (lenStr != null) {
                  try {
                      val len = lenStr.toLong()
                      if (len > MAX_UPLOAD_SIZE) {
                           return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Payload too large")
                      }
                  } catch (e: Exception) {}
             } else {
                 return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Content-Length required")
             }
        }

        // Simple Token Auth
        val requestToken = params["token"]
        if (!MessageDigest.isEqual(token.toByteArray(), (requestToken ?: "").toByteArray())) {
             return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized")
        }

        if (uri == "/api/config" && method == Method.GET) {
            val json = JSONObject()
            json.put("global_mode", fileExists("global_mode"))
            json.put("tee_broken_mode", fileExists("tee_broken_mode"))
            json.put("rkp_bypass", fileExists("rkp_bypass"))
            json.put("auto_beta", fileExists("auto_beta_fetch"))
            json.put("auto_keybox_check", fileExists("auto_keybox_check"))
            val files = JSONArray()
            files.put("keybox.xml")
            files.put("target.txt")
            files.put("security_patch.txt")
            files.put("spoof_build_vars")
            files.put("app_config")
            json.put("files", files)
            json.put("keybox_count", CertHack.getKeyboxCount())
            val templates = JSONArray()
            Config.getTemplateNames().forEach { name ->
                templates.put(name)
            }
            json.put("templates", templates)
            return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        }

        // NEW: Get Templates List
        if (uri == "/api/templates" && method == Method.GET) {
            val templates = DeviceTemplateManager.listTemplates()
            val array = JSONArray()
            templates.forEach { t ->
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("model", t.model)
                obj.put("manufacturer", t.manufacturer)
                obj.put("fingerprint", t.fingerprint)
                obj.put("securityPatch", t.securityPatch)
                array.put(obj)
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
        }

        // NEW: Get Installed Packages
        if (uri == "/api/packages" && method == Method.GET) {
            return try {
                val p = Runtime.getRuntime().exec("pm list packages")
                val output = p.inputStream.bufferedReader().readText()
                val packages = output.lines()
                    .filter { it.startsWith("package:") }
                    .map { it.removePrefix("package:").trim() }
                    .sorted()
                val array = JSONArray(packages)
                newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed to list packages")
            }
        }

        // NEW: Get Structured App Config
        if (uri == "/api/app_config_structured" && method == Method.GET) {
            val file = File(configDir, "app_config")
            val array = JSONArray()
            if (file.exists()) {
                file.useLines { lines ->
                    lines.forEach { line ->
                        if (line.isNotBlank() && !line.startsWith("#")) {
                            val parts = line.trim().split(Regex("\\s+"))
                            if (parts.isNotEmpty()) {
                                val obj = JSONObject()
                                obj.put("package", parts[0])
                                obj.put("template", if (parts.size > 1 && parts[1] != "null") parts[1] else "")
                                obj.put("keybox", if (parts.size > 2 && parts[2] != "null") parts[2] else "")
                                array.put(obj)
                            }
                        }
                    }
                }
            }
            return newFixedLengthResponse(Response.Status.OK, "application/json", array.toString())
        }

        // NEW: Save Structured App Config
        if (uri == "/api/app_config_structured" && method == Method.POST) {
             val map = HashMap<String, String>()
             try { session.parseBody(map) } catch(e:Exception){}
             val jsonStr = session.parms["data"]

             if (jsonStr != null) {
                 try {
                     val array = JSONArray(jsonStr)
                     val sb = StringBuilder()
                     sb.append("# Generated by WebUI\n")
                     for (i in 0 until array.length()) {
                         val obj = array.getJSONObject(i)
                         val pkg = obj.getString("package")
                         val tmpl = obj.optString("template", "null").ifEmpty { "null" }
                         val kb = obj.optString("keybox", "null").ifEmpty { "null" }

                         if (pkg.contains(Regex("\\s")) || tmpl.contains(Regex("\\s")) || kb.contains(Regex("\\s"))) {
                             return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid input: whitespace not allowed")
                         }

                         sb.append("$pkg $tmpl $kb\n")
                     }
                     if (saveFile("app_config", sb.toString())) {
                         // Trigger reload
                         File(configDir, "app_config").setLastModified(System.currentTimeMillis())
                         return newFixedLengthResponse(Response.Status.OK, "text/plain", "Saved")
                     }
                 } catch (e: Exception) {
                     return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid JSON")
                 }
             }
             return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed")
        }

        if (uri == "/api/file" && method == Method.GET) {
            val filename = params["filename"]
            if (filename != null && isValidFilename(filename)) {
                return newFixedLengthResponse(Response.Status.OK, "text/plain", readFile(filename))
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid filename")
        }

        if (uri == "/api/save" && method == Method.POST) {
             val map = HashMap<String, String>()
             try { session.parseBody(map) } catch(e:Exception){}
             val filename = session.parms["filename"]
             val content = session.parms["content"]

             if (filename != null && isValidFilename(filename) && content != null) {
                 if (saveFile(filename, content)) {
                     return newFixedLengthResponse(Response.Status.OK, "text/plain", "Saved")
                 }
             }
             return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed")
        }

        if (uri == "/api/upload_keybox" && method == Method.POST) {
             val map = HashMap<String, String>()
             try { session.parseBody(map) } catch(e:Exception){}
             val filename = session.parms["filename"]
             val content = session.parms["content"]

             if (filename != null && content != null && filename.endsWith(".xml") && !filename.contains("/")) {
                 val keyboxDir = File(configDir, "keyboxes")
                 if (!keyboxDir.exists()) {
                     keyboxDir.mkdirs()
                     permissionSetter(keyboxDir, 448) // 0700
                 }

                 val file = File(keyboxDir, filename)
                 try {
                     file.writeText(content)
                     permissionSetter(file, 384) // 0600
                     return newFixedLengthResponse(Response.Status.OK, "text/plain", "Saved")
                 } catch (e: Exception) {
                     e.printStackTrace()
                     return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed: " + e.message)
                 }
             }
             return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid request")
        }

        if (uri == "/api/verify_keyboxes" && method == Method.POST) {
             try {
                val results = KeyboxVerifier.verify(configDir)
                val json = createKeyboxVerificationJson(results)
                return newFixedLengthResponse(Response.Status.OK, "application/json", json)
             } catch(e: Exception) {
                 return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
             }
        }

        if (uri == "/api/toggle" && method == Method.POST) {
             val map = HashMap<String, String>()
             try { session.parseBody(map) } catch(e:Exception){}
             val setting = session.parms["setting"]
             val value = session.parms["value"]

             if (setting != null && value != null) {
                 if (toggleFile(setting, value.toBoolean())) {
                     return newFixedLengthResponse(Response.Status.OK, "text/plain", "Toggled")
                 }
             }
             return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed")
        }

        if (uri == "/api/reload" && method == Method.POST) {
             try {
                File(configDir, "target.txt").setLastModified(System.currentTimeMillis())
                return newFixedLengthResponse(Response.Status.OK, "text/plain", "Reloaded")
             } catch(e: Exception) {
                 return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed")
             }
        }

        if (uri == "/api/fetch_beta" && method == Method.POST) {
             try {
                val result = BetaFetcher.fetchAndApply(null)
                if (result.success) {
                    return newFixedLengthResponse(Response.Status.OK, "text/plain", "Success: ${result.profile?.model}")
                } else {
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed: ${result.error}")
                }
             } catch(e: Exception) {
                 return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
             }
        }

        // NEW: Community Stats
        if (uri == "/api/stats" && method == Method.GET) {
            val count = fetchTelegramCount()
            val json = JSONObject()
            json.put("members", count)
            return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
        }

        if (uri == "/" || uri == "/index.html") {
            return newFixedLengthResponse(Response.Status.OK, "text/html", getHtml())
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    private fun isValidFilename(name: String): Boolean {
        return name in setOf("target.txt", "security_patch.txt", "spoof_build_vars", "app_config", "templates.json")
    }

    private fun getHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>CleveresTricky GOD-MODE</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        :root { --bg: #0a0a0a; --fg: #e0e0e0; --accent: #00ff9d; --panel: #161616; --border: #333; }
        body { background-color: var(--bg); color: var(--fg); font-family: 'JetBrains Mono', monospace; margin: 0; padding: 0; }
        h1 { text-align: center; font-weight: 300; letter-spacing: 4px; margin: 20px 0; color: #fff; text-shadow: 0 0 10px rgba(255,255,255,0.2); }
        .tabs { display: flex; justify-content: center; border-bottom: 1px solid var(--border); background: var(--panel); }
        .tab { padding: 15px 25px; cursor: pointer; border-bottom: 2px solid transparent; opacity: 0.7; transition: all 0.3s; }
        .tab:hover { opacity: 1; }
        .tab.active { border-bottom-color: var(--accent); opacity: 1; color: var(--accent); }
        .content { display: none; padding: 20px; max-width: 900px; margin: 0 auto; }
        .content.active { display: block; }
        .panel { background: var(--panel); border: 1px solid var(--border); padding: 20px; margin-bottom: 20px; }
        .row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 15px; }
        button { background: transparent; border: 1px solid var(--fg); color: var(--fg); padding: 8px 16px; cursor: pointer; font-family: inherit; text-transform: uppercase; transition: all 0.2s; }
        button:hover { background: var(--fg); color: var(--bg); }
        button.primary { border-color: var(--accent); color: var(--accent); }
        button.primary:hover { background: var(--accent); color: #000; box-shadow: 0 0 15px var(--accent); }
        input[type="text"], textarea, select { background: #000; border: 1px solid var(--border); color: #fff; padding: 10px; width: 100%; box-sizing: border-box; font-family: inherit; }
        input[type="checkbox"] { transform: scale(1.5); accent-color: var(--accent); }
        .status { color: #888; font-size: 0.9em; margin-top: 5px; }
        .toast { position: fixed; bottom: 30px; left: 50%; transform: translateX(-50%); background: var(--accent); color: #000; padding: 12px 24px; font-weight: bold; opacity: 0; pointer-events: none; transition: opacity 0.3s; z-index: 999; }
        .toast.show { opacity: 1; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { text-align: left; padding: 10px; border-bottom: 1px solid var(--border); }
        th { color: #888; font-size: 0.8em; text-transform: uppercase; }
        .badge { display: inline-block; padding: 2px 6px; border: 1px solid #555; font-size: 0.7em; margin-right: 5px; }
        *:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
    </style>
</head>
<body>
    <h1>CLEVERESTRICKY <span style="font-size:0.4em; color:var(--accent); vertical-align:middle;">GOD-MODE</span></h1>

    <div class="tabs" role="tablist">
        <div class="tab active" id="tab_dashboard" role="tab" tabindex="0" aria-selected="true" onclick="switchTab('dashboard')">DASHBOARD</div>
        <div class="tab" id="tab_lab" role="tab" tabindex="0" aria-selected="false" onclick="switchTab('lab')">SPOOFING LAB</div>
        <div class="tab" id="tab_apps" role="tab" tabindex="0" aria-selected="false" onclick="switchTab('apps')">APP CONFIG</div>
        <div class="tab" id="tab_keys" role="tab" tabindex="0" aria-selected="false" onclick="switchTab('keys')">KEYBOXES</div>
        <div class="tab" id="tab_editor" role="tab" tabindex="0" aria-selected="false" onclick="switchTab('editor')">EDITOR</div>
    </div>

    <!-- DASHBOARD -->
    <div id="dashboard" class="content active">
        <div class="panel">
            <div class="row"><label for="global_mode">Global Mode</label><input type="checkbox" id="global_mode" onchange="toggle('global_mode')"></div>
            <div class="row"><label for="tee_broken_mode">TEE Broken Mode</label><input type="checkbox" id="tee_broken_mode" onchange="toggle('tee_broken_mode')"></div>
            <div class="row"><label for="rkp_bypass">RKP Bypass (Strong)</label><input type="checkbox" id="rkp_bypass" onchange="toggle('rkp_bypass')"></div>
            <div class="row"><label for="auto_beta_fetch">Auto Beta Fetch</label><input type="checkbox" id="auto_beta_fetch" onchange="toggle('auto_beta_fetch')"></div>
            <div class="row"><label for="auto_keybox_check">Auto Keybox Check</label><input type="checkbox" id="auto_keybox_check" onchange="toggle('auto_keybox_check')"></div>
            <div class="row" style="margin-top:20px;">
                <div id="keyboxStatus" aria-live="polite">Loading keys...</div>
                <button onclick="runWithState(this, 'RELOADING...', reloadConfig)" id="reloadBtn">RELOAD CONFIG</button>
            </div>
        </div>

        <!-- NEW COMMUNITY SECTION -->
        <div class="panel" style="text-align:center; border: 1px solid var(--accent);">
            <h3 style="margin:5px 0; color:var(--accent); letter-spacing:2px;">COMMUNITY POWERED</h3>
            <div id="communityCount" style="font-size:2.5em; font-weight:300;">Loading...</div>
            <div style="font-size:0.8em; color:#888; margin-bottom:5px;">TELEGRAM MEMBERS</div>
            <a href="https://t.me/cleverestech" target="_blank" rel="noopener noreferrer" style="color:#fff; text-decoration:none; font-size:0.8em; border-bottom:1px dotted #fff;">JOIN US</a>
        </div>
    </div>

    <!-- SPOOFING LAB -->
    <div id="lab" class="content">
        <div class="panel">
            <h3>IDENTITY SELECTOR</h3>
            <p style="color:#888; font-size:0.9em;">Select a verified device identity to spoof globally or save for specific apps.</p>
            <select id="templateSelect" onchange="previewTemplate()" aria-label="Device Identity Selector"></select>

            <div id="templatePreview" style="margin-top:15px; border:1px solid var(--border); padding:15px; display:none;">
                <div class="row"><span>Model:</span> <b id="pModel"></b></div>
                <div class="row"><span>Manufacturer:</span> <span id="pManuf"></span></div>
                <div class="row"><span>Security Patch:</span> <span id="pPatch"></span></div>
                <div class="row" style="margin-top: 5px; align-items: flex-start;">
                    <div style="font-size:0.7em; color:#666; word-break:break-all; padding-right: 10px;" id="pFing"></div>
                    <button onclick="copyFingerprint(this)" style="padding: 4px 8px; font-size: 0.7em; white-space: nowrap;" aria-label="Copy fingerprint">COPY</button>
                </div>
            </div>

            <div class="row" style="margin-top:15px;">
                <button onclick="runWithState(this, 'APPLYING...', applyTemplateToGlobal)">APPLY GLOBALLY</button>
                <button class="primary" onclick="switchTab('apps')">USE IN APP CONFIG</button>
            </div>
        </div>
        <div class="panel">
            <h3>BETA FETCHER</h3>
            <button onclick="runWithState(this, 'FETCHING...', fetchBetaNow)">FETCH LATEST PIXEL BETA</button>
        </div>
    </div>

    <!-- APP CONFIG -->
    <div id="apps" class="content">
        <div class="panel">
            <h3>ADD NEW RULE</h3>
            <div style="margin-bottom:10px;">
                <label for="appPkg" style="font-size:0.8em; color:#888;">TARGET PACKAGE</label>
                <input type="text" id="appPkg" list="pkgList" placeholder="com.example.app" aria-label="Target Package">
                <datalist id="pkgList"></datalist>
            </div>
            <div style="margin-bottom:10px;">
                <label for="appTemplate" style="font-size:0.8em; color:#888;">DEVICE IDENTITY</label>
                <select id="appTemplate">
                    <option value="null">Default (No Spoof)</option>
                </select>
            </div>
            <div style="margin-bottom:15px;">
                <label for="appKeybox" style="font-size:0.8em; color:#888;">KEYBOX OVERRIDE</label>
                <input type="text" id="appKeybox" placeholder="keybox.xml (optional)">
            </div>
            <button class="primary" onclick="addAppRule()">ADD RULE</button>
        </div>

        <div class="panel">
            <h3>ACTIVE RULES</h3>
            <table id="appTable">
                <thead><tr><th>Package</th><th>Template</th><th>Keybox</th><th>Action</th></tr></thead>
                <tbody></tbody>
            </table>
            <div class="row" style="margin-top:15px; justify-content:flex-end;">
                <button onclick="runWithState(this, 'SAVING...', saveAppConfig)" class="primary">SAVE CHANGES</button>
            </div>
        </div>
    </div>

    <!-- KEYBOXES -->
    <div id="keys" class="content">
        <div class="panel">
            <h3>UPLOAD KEYBOX</h3>
            <input type="file" id="kbFilePicker" style="display:none" onchange="loadFileContent(this)" onclick="this.value = null">
            <div style="display:flex; gap:10px; margin-bottom:10px;">
                <button onclick="document.getElementById('kbFilePicker').click()" style="flex:1;">📂 LOAD FROM FILE</button>
            </div>
            <input type="text" id="kbFilename" placeholder="filename.xml" aria-label="Keybox Filename">
            <textarea id="kbContent" placeholder="Paste XML content..." style="height:100px; margin-top:10px;" aria-label="Keybox Content"></textarea>
            <button onclick="runWithState(this, 'UPLOADING...', uploadKeybox)" style="width:100%; margin-top:10px;">UPLOAD</button>
        </div>
        <div class="panel">
            <div class="row">
                <h3>VERIFICATION</h3>
                <button onclick="runWithState(this, 'CHECKING...', verifyKeyboxes)">RUN CHECK</button>
            </div>
            <div id="verifyResult"></div>
        </div>
    </div>

    <!-- EDITOR -->
    <div id="editor" class="content">
        <div class="panel">
            <select id="fileSelector" onchange="loadFile()" aria-label="Select configuration file">
                <option value="target.txt">target.txt</option>
                <option value="security_patch.txt">security_patch.txt</option>
                <option value="spoof_build_vars">spoof_build_vars</option>
                <option value="custom_templates">custom_templates</option>
                <option value="templates.json">templates.json</option>
            </select>
            <textarea id="fileEditor" style="height:400px; margin-top:10px;" aria-label="Configuration editor"></textarea>
            <button onclick="runWithState(this, 'SAVING...', saveFile)" style="width:100%; margin-top:10px;" id="saveBtn">SAVE FILE</button>
        </div>
    </div>

    <div id="toast" class="toast" role="alert" aria-live="assertive">Action Successful</div>

    <script>
        const baseUrl = '/api';
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('token');

        function getAuthUrl(path) { return path + (path.includes('?') ? '&' : '?') + 'token=' + token; }
        function showToast(msg) {
            const t = document.getElementById('toast');
            t.innerText = msg;
            t.classList.add('show');
            setTimeout(() => t.classList.remove('show'), 3000);
        }

        async function runWithState(btn, text, task) {
             const orig = btn.innerText;
             btn.disabled = true;
             btn.innerText = text;
             try { await task(); }
             finally { btn.disabled = false; btn.innerText = orig; }
        }

        function switchTab(id) {
            document.querySelectorAll('.tab').forEach(t => {
                t.classList.remove('active');
                t.setAttribute('aria-selected', 'false');
            });
            document.querySelectorAll('.content').forEach(c => c.classList.remove('active'));
            const activeTab = document.getElementById('tab_' + id);
            if (activeTab) {
                activeTab.classList.add('active');
                activeTab.setAttribute('aria-selected', 'true');
            }
            document.getElementById(id).classList.add('active');
            if (id === 'apps') loadAppConfig();
        }

        async function init() {
            if (!token) return;
            // Load Config
            const res = await fetch(getAuthUrl('/api/config'));
            const data = await res.json();
            ['global_mode', 'tee_broken_mode', 'rkp_bypass', 'auto_beta_fetch', 'auto_keybox_check'].forEach(k => {
                if(document.getElementById(k)) document.getElementById(k).checked = data[k];
            });
            document.getElementById('keyboxStatus').innerText = `Active Keys: ${'$'}{data.keybox_count}`;

            // Load Community Stats
            fetch(getAuthUrl('/api/stats'))
                .then(r => r.json())
                .then(data => {
                    document.getElementById('communityCount').innerText = data.members;
                })
                .catch(() => {
                    document.getElementById('communityCount').innerText = 'Offline';
                });

            // Load Templates
            const tRes = await fetch(getAuthUrl('/api/templates'));
            const templates = await tRes.json();
            const sel = document.getElementById('templateSelect');
            const appSel = document.getElementById('appTemplate');

            templates.forEach(t => {
                const opt = document.createElement('option');
                opt.value = t.id;
                opt.text = `${'$'}{t.model} (${'$'}{t.manufacturer})`;
                opt.dataset.json = JSON.stringify(t);
                sel.appendChild(opt.cloneNode(true));
                appSel.appendChild(opt);
            });
            previewTemplate();

            // Load Packages (Async)
            fetch(getAuthUrl('/api/packages')).then(r => r.json()).then(pkgs => {
                const dl = document.getElementById('pkgList');
                pkgs.forEach(p => {
                    const opt = document.createElement('option');
                    opt.value = p;
                    dl.appendChild(opt);
                });
            });

            // Keyboard Nav
            document.querySelectorAll('.tab').forEach(t => {
                t.addEventListener('keydown', e => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        t.click();
                    }
                });
            });

            // Init Editor
            currentFile = document.getElementById('fileSelector').value;
            await loadFile();
            document.getElementById('fileEditor').addEventListener('input', () => {
                editorDirty = true;
                document.getElementById('saveBtn').innerText = 'SAVE FILE *';
            });
        }

        async function toggle(setting) {
            const el = document.getElementById(setting);
            el.disabled = true;
            try {
                await fetch(getAuthUrl('/api/toggle'), {
                    method: 'POST',
                    body: new URLSearchParams({setting, value: el.checked})
                });
                showToast('SETTING UPDATED');
            } catch(e) { el.checked = !el.checked; showToast('UPDATE FAILED'); }
            finally { el.disabled = false; }
        }

        function previewTemplate() {
            const sel = document.getElementById('templateSelect');
            const t = JSON.parse(sel.selectedOptions[0].dataset.json);
            document.getElementById('pModel').innerText = t.model;
            document.getElementById('pManuf').innerText = t.manufacturer;
            document.getElementById('pPatch').innerText = t.securityPatch;
            document.getElementById('pFing').innerText = t.fingerprint;
            document.getElementById('templatePreview').style.display = 'block';
        }

        let appRules = [];

        async function loadAppConfig() {
            const res = await fetch(getAuthUrl('/api/app_config_structured'));
            appRules = await res.json();
            renderAppTable();
        }

        function renderAppTable() {
            const tbody = document.querySelector('#appTable tbody');
            tbody.innerHTML = '';

            if (appRules.length === 0) {
                const tr = document.createElement('tr');
                const td = document.createElement('td');
                td.colSpan = 4;
                td.style.textAlign = 'center';
                td.style.padding = '20px';
                td.style.color = '#888';
                td.style.fontStyle = 'italic';
                td.innerText = 'No active rules. Add a package above.';
                tr.appendChild(td);
                tbody.appendChild(tr);
                return;
            }

            appRules.forEach((rule, idx) => {
                const tr = document.createElement('tr');

                const tdPkg = document.createElement('td');
                tdPkg.textContent = rule.package;
                tr.appendChild(tdPkg);

                const tdTmpl = document.createElement('td');
                tdTmpl.textContent = rule.template || '-';
                tr.appendChild(tdTmpl);

                const tdKb = document.createElement('td');
                tdKb.textContent = rule.keybox || '-';
                tr.appendChild(tdKb);

                const tdAction = document.createElement('td');
                const btn = document.createElement('button');
                btn.textContent = 'DEL';
                btn.dataset.state = 'initial';
                btn.onclick = function() {
                    if (this.dataset.state === 'initial') {
                        this.dataset.state = 'confirm';
                        this.textContent = 'SURE?';
                        this.style.borderColor = '#f00';
                        this.style.color = '#f00';
                        const self = this;
                        this.timer = setTimeout(function() {
                             self.dataset.state = 'initial';
                             self.textContent = 'DEL';
                             self.style.borderColor = '';
                             self.style.color = '';
                        }, 3000);
                    } else {
                        clearTimeout(this.timer);
                        removeAppRule(idx);
                    }
                };
                btn.ariaLabel = `Delete rule for ${'$'}{rule.package}`;
                btn.style.padding = '2px 8px';
                btn.style.fontSize = '0.8em';
                tdAction.appendChild(btn);
                tr.appendChild(tdAction);

                tbody.appendChild(tr);
            });
        }

        function addAppRule() {
            const pkg = document.getElementById('appPkg').value;
            const tmpl = document.getElementById('appTemplate').value;
            const kb = document.getElementById('appKeybox').value;
            if (!pkg) { showToast('Package required'); return; }
            appRules.push({ package: pkg, template: tmpl === 'null' ? '' : tmpl, keybox: kb });
            renderAppTable();
            document.getElementById('appPkg').value = '';
        }

        function removeAppRule(idx) {
            appRules.splice(idx, 1);
            renderAppTable();
        }

        async function saveAppConfig() {
            try {
                await fetch(getAuthUrl('/api/app_config_structured'), {
                    method: 'POST',
                    body: new URLSearchParams({ data: JSON.stringify(appRules) })
                });
                showToast('Saved Rules');
            } catch(e) { showToast('Save Failed'); }
        }

        async function applyTemplateToGlobal() {
             if (!confirm('This will overwrite spoof_build_vars. Continue?')) return;
             const sel = document.getElementById('templateSelect');
             const t = JSON.parse(sel.selectedOptions[0].dataset.json);
             const content = `TEMPLATE=${'$'}{t.id}\n# Applied via WebUI\n`;

             await fetch(getAuthUrl('/api/save'), {
                 method: 'POST',
                 body: new URLSearchParams({ filename: 'spoof_build_vars', content })
             });
             showToast('Applied Globally');
             setTimeout(reloadConfig, 500);
        }

        async function reloadConfig() {
            await fetch(getAuthUrl('/api/reload'), { method: 'POST' });
            showToast('Config Reloaded');
            setTimeout(() => window.location.reload(), 1000);
        }

        let editorDirty = false;
        let currentFile = '';

        async function loadFile() {
            const sel = document.getElementById('fileSelector');
            const f = sel.value;
            if (editorDirty && currentFile && currentFile !== f) {
                if (!confirm('You have unsaved changes. Discard them?')) {
                    sel.value = currentFile;
                    return;
                }
            }
            currentFile = f;
            const res = await fetch(getAuthUrl('/api/file?filename=' + f));
            document.getElementById('fileEditor').value = await res.text();
            editorDirty = false;
            document.getElementById('saveBtn').innerText = 'SAVE FILE';
        }

        async function saveFile() {
            const f = document.getElementById('fileSelector').value;
            const c = document.getElementById('fileEditor').value;
            await fetch(getAuthUrl('/api/save'), {
                 method: 'POST',
                 body: new URLSearchParams({ filename: f, content: c })
             });
             showToast('File Saved');
             editorDirty = false;
             document.getElementById('saveBtn').innerText = 'SAVE FILE';
        }

        async function fetchBetaNow() {
             showToast('Fetching Beta...');
             const res = await fetch(getAuthUrl('/api/fetch_beta'), { method: 'POST' });
             if(res.ok) showToast('Success'); else showToast('Failed');
        }

        async function uploadKeybox() {
            const f = document.getElementById('kbFilename').value;
            const c = document.getElementById('kbContent').value;
            if (!f || !c) return;
            await fetch(getAuthUrl('/api/upload_keybox'), {
                 method: 'POST',
                 body: new URLSearchParams({ filename: f, content: c })
             });
             showToast('Uploaded');
        }

        function loadFileContent(input) {
            if (input.files && input.files[0]) {
                const file = input.files[0];
                document.getElementById('kbFilename').value = file.name;
                const reader = new FileReader();
                reader.onload = (e) => {
                    document.getElementById('kbContent').value = e.target.result;
                };
                reader.readAsText(file);
            }
        }

        function copyFingerprint(btn) {
             const text = document.getElementById('pFing').innerText;
             if (text) {
                 navigator.clipboard.writeText(text).then(() => {
                     showToast('COPIED TO CLIPBOARD');
                     if (btn) {
                        const original = btn.innerText;
                        btn.innerText = 'COPIED!';
                        setTimeout(() => btn.innerText = original, 2000);
                     }
                 }).catch(() => {
                     showToast('COPY FAILED');
                 });
             }
        }

        async function verifyKeyboxes() {
             showToast('Verifying...');
             const res = await fetch(getAuthUrl('/api/verify_keyboxes'), { method: 'POST' });
             const data = await res.json();
             const container = document.getElementById('verifyResult');
             container.innerHTML = '';
             if (data.length === 0) {
                 const div = document.createElement('div');
                 div.style.color = '#888';
                 div.style.fontStyle = 'italic';
                 div.style.marginTop = '10px';
                 div.innerText = 'No keyboxes found. Upload one above or place in /data/adb/cleverestricky/keyboxes/';
                 container.appendChild(div);
             }
             data.forEach(d => {
                 const div = document.createElement('div');
                 div.style.color = d.status === 'VALID' ? '#0f0' : '#f00';
                 div.innerText = `[${'$'}{d.status}] ${'$'}{d.filename}`;
                 if (d.details) {
                     const details = document.createElement('div');
                     details.style.color = '#888';
                     details.style.fontSize = '0.8em';
                     details.style.marginLeft = '10px';
                     details.innerText = d.details;
                     div.appendChild(details);
                 }
                 container.appendChild(div);
             });
        }

        init();
    </script>
</body>
</html>
        """.trimIndent()
    }

    companion object {
        fun createKeyboxVerificationJson(results: List<KeyboxVerifier.Result>): String {
            val array = JSONArray()
            results.forEach { res ->
                val obj = JSONObject()
                obj.put("filename", res.filename)
                obj.put("status", res.status.toString())
                obj.put("details", res.details)
                array.put(obj)
            }
            return array.toString()
        }
    }
}
