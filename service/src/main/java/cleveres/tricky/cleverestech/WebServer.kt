package cleveres.tricky.cleverestech

import android.system.Os
import cleveres.tricky.cleverestech.keystore.CertHack
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class WebServer(port: Int, private val configDir: File = File("/data/adb/cleverestricky")) : NanoHTTPD("127.0.0.1", port) {

    val token = UUID.randomUUID().toString()

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
            try {
                Os.chmod(f.absolutePath, 384)
            } catch (t: Throwable) {
                Logger.e("failed to set permissions for $filename", t)
            }
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
        return name in setOf("global_mode", "tee_broken_mode", "rkp_bypass", "auto_beta_fetch")
    }

    private fun toggleFile(filename: String, enable: Boolean): Boolean {
        if (!isValidSetting(filename)) return false
        val f = File(configDir, filename)
        return try {
            if (enable) {
                if (!f.exists()) {
                    f.createNewFile()
                    try {
                        Os.chmod(f.absolutePath, 384) // 0600
                    } catch (t: Throwable) {
                        Logger.e("failed to set permissions for $filename", t)
                    }
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

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        val params = session.parms

        // Simple Token Auth
        val requestToken = params["token"]
        if (!MessageDigest.isEqual(token.toByteArray(), (requestToken ?: "").toByteArray())) {
             return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/plain", "Unauthorized")
        }

        if (uri == "/api/config" && method == Method.GET) {
            val config = StringBuilder("{")
            config.append("\"global_mode\": ${fileExists("global_mode")},")
            config.append("\"tee_broken_mode\": ${fileExists("tee_broken_mode")},")
            config.append("\"rkp_bypass\": ${fileExists("rkp_bypass")},")
            config.append("\"auto_beta\": ${fileExists("auto_beta_fetch")},")
            // For file contents, we might want to load them on demand or include them here.
            // Including them might be heavy if large. Let's make separate endpoints or just load them if requested?
            // For simplicity, let's load text files separately or all at once?
            // Let's load them via separate API calls or just embed them in the HTML initial load?
            // Let's return the toggles here.
            config.append("\"files\": [\"keybox.xml\", \"target.txt\", \"security_patch.txt\", \"spoof_build_vars\"],")
            config.append("\"keybox_count\": ${CertHack.getKeyboxCount()},")
            config.append("\"templates\": [")
            Config.getTemplateNames().forEachIndexed { index, name ->
                if (index > 0) config.append(",")
                config.append("\"$name\"")
            }
            config.append("]")
            config.append("}")
            return newFixedLengthResponse(Response.Status.OK, "application/json", config.toString())
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
             // nanohttpd puts body content in map["postData"] usually, or mix params.
             // When parsing body, map contains temp file paths for uploads, but for form data it puts in parms?
             // Actually parseBody populates map with temp files, and parms with fields.
             val content = session.parms["content"] // This might be limited in size?
             // For large content (keybox), we might need to read from map if it's treated as file?
             // But usually standard form post puts it in parms.
             // Let's assume text/plain body or form-url-encoded.

             // Better way: read body directly if it's just raw text?
             // Let's stick to simple form post or json.
             // If I use fetch with JSON body, I need to parse it.
             // Let's use simple x-www-form-urlencoded.

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
                 if (!keyboxDir.exists()) keyboxDir.mkdirs()

                 val file = File(keyboxDir, filename)
                 try {
                     file.writeText(content)
                     return newFixedLengthResponse(Response.Status.OK, "text/plain", "Saved")
                 } catch (e: Exception) {
                     e.printStackTrace()
                     return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed: " + e.message)
                 }
             }
             return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Invalid request")
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
                // Trigger fetch via shell script or direct call if possible.
                // Since this runs as root/system, we can maybe trigger the service directly or run the script.
                // For simplicity, let's just touch a trigger file or run the command.
                // But BetaFetcher is in the same process potentially? No, this is the service.
                // BetaFetcher is an object in the same package.
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

        if (uri == "/" || uri == "/index.html") {
            return newFixedLengthResponse(Response.Status.OK, "text/html", getHtml())
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
    }

    private fun isValidFilename(name: String): Boolean {
        return name in setOf("keybox.xml", "target.txt", "security_patch.txt", "spoof_build_vars")
    }

    private fun getHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>CleveresTricky</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <style>
        body { background-color: #000000; color: #ffffff; font-family: sans-serif; padding: 20px; max-width: 800px; margin: 0 auto; }
        h1 { text-align: center; font-weight: 300; letter-spacing: 2px; }
        .section { margin-bottom: 20px; padding: 15px; border: 1px solid #333; border-radius: 0; }
        .row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
        button { padding: 10px 20px; background-color: #000; color: #fff; border: 1px solid #fff; cursor: pointer; font-size: 14px; text-transform: uppercase; letter-spacing: 1px; transition: background 0.2s, color 0.2s; }
        button:hover { background-color: #fff; color: #000; }
        button:disabled { border-color: #555; color: #555; }
        textarea, input[type="text"], select { width: 100%; background-color: #000; color: #fff; border: 1px solid #555; padding: 10px; font-family: monospace; box-sizing: border-box; }
        select { margin-bottom: 10px; }
        input[type="checkbox"] { accent-color: #fff; transform: scale(1.2); }
        .status { text-align: center; color: #888; margin-top: 10px; font-family: monospace; }
        .footer { margin-top: 40px; border-top: 1px solid #333; padding-top: 20px; font-size: 0.8em; color: #666; text-align: center; }
        .footer a { color: #fff; text-decoration: none; border-bottom: 1px dotted #666; }
        .toast { position: fixed; bottom: 20px; left: 50%; transform: translateX(-50%); background-color: #fff; color: #000; padding: 10px 20px; border: 1px solid #000; opacity: 0; transition: opacity 0.3s; pointer-events: none; z-index: 1000; font-family: monospace; }
        .toast.show { opacity: 1; }
    </style>
</head>
<body>
    <h1>CLEVERESTRICKY <span style="font-size: 0.4em; border: 1px solid #fff; padding: 2px 4px; vertical-align: middle;">BETA</span></h1>

    <div class="section">
        <div class="row"><label for="global_mode">Global Mode</label><input type="checkbox" id="global_mode" onchange="toggle('global_mode')"></div>
        <div class="row"><label for="tee_broken_mode">TEE Broken Mode</label><input type="checkbox" id="tee_broken_mode" onchange="toggle('tee_broken_mode')"></div>
        <div class="row"><label for="rkp_bypass">RKP Bypass (Strong Integrity)</label><input type="checkbox" id="rkp_bypass" onchange="toggle('rkp_bypass')"></div>
        <div class="row"><label for="auto_beta_fetch">Auto Pixel Beta Fetch (Daily)</label><input type="checkbox" id="auto_beta_fetch" onchange="toggle('auto_beta_fetch')"></div>
        <div class="row" style="margin-top: 15px; justify-content: flex-end;">
            <button onclick="fetchBetaNow()">Fetch Beta Fingerprint</button>
        </div>
        <div class="status" id="keyboxStatus" aria-live="polite">Keybox Status: Loading...</div>
    </div>

    <div class="section">
        <h3 style="font-weight: normal; border-bottom: 1px solid #333; padding-bottom: 5px;">KEYBOX JUKEBOX</h3>
        <div style="margin-bottom: 15px; font-size: 0.8em; color: #888;">Upload multiple XML files to automatically rotate keys.</div>
        <input type="text" id="kbFilename" placeholder="filename.xml (e.g., keybox_01.xml)" style="margin-bottom: 10px;">
        <textarea id="kbContent" placeholder="Paste keybox.xml content here..." style="height: 100px; margin-bottom: 10px;"></textarea>
        <div class="row" style="justify-content: flex-end;">
            <button onclick="uploadKeybox()" style="width: 100%;">UPLOAD TO POOL</button>
        </div>
    </div>

    <div class="section">
        <select id="fileSelector" onchange="loadFile()" aria-label="Select configuration file">
            <option value="keybox.xml">keybox.xml</option>
            <option value="target.txt">target.txt</option>
            <option value="security_patch.txt">security_patch.txt</option>
            <option value="spoof_build_vars">spoof_build_vars</option>
        </select>

        <div id="templateSection" style="display:none; margin-bottom: 10px;">
             <div class="row">
                <select id="templateSelector" style="flex-grow: 1; margin-right: 10px;" aria-label="Select device template">
                    <option value="" disabled selected>Select template...</option>
                </select>
                <button onclick="applyTemplate()">LOAD</button>
             </div>
        </div>

        <textarea id="editor" aria-label="Configuration editor" style="height: 300px;"></textarea>
        <div class="row" style="margin-top: 10px; justify-content: flex-end;">
            <button id="saveBtn" onclick="saveFile()">SAVE FILE</button>
        </div>
    </div>

    <div class="section" style="text-align: center; border: none;">
        <button id="reloadBtn" onclick="reloadConfig()" style="width: 100%;">RELOAD CONFIG</button>
    </div>

    <div class="footer">
        Designed with simplicity for system optimization and performance.<br>
        <br>
        <a href="https://t.me/cleverestech">t.me/cleverestech</a>
    </div>

    <script>
        const baseUrl = '/api';
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('token');

        function getAuthUrl(path) {
            return path + (path.includes('?') ? '&' : '?') + 'token=' + token;
        }

        let toastTimeout;
        function showToast(msg) {
            let t = document.getElementById('toast');
            if (!t) { t = document.createElement('div'); t.id = 'toast'; t.className = 'toast'; document.body.appendChild(t); }
            t.textContent = msg; t.className = 'toast show';
            if (toastTimeout) clearTimeout(toastTimeout);
            toastTimeout = setTimeout(() => { t.className = t.className.replace('show', ''); }, 3000);
        }

        async function init() {
            if (!token) {
                document.body.innerHTML = '<h1 style="color:white;text-align:center;margin-top:50px;">UNAUTHORIZED</h1>';
                return;
            }
            const res = await fetch(getAuthUrl(baseUrl + '/config'));
            if (!res.ok) {
                alert('Failed to load config: ' + res.status);
                return;
            }
            const data = await res.json();
            document.getElementById('global_mode').checked = data.global_mode;
            document.getElementById('tee_broken_mode').checked = data.tee_broken_mode;
            document.getElementById('tee_broken_mode').checked = data.tee_broken_mode;
            document.getElementById('rkp_bypass').checked = data.rkp_bypass;
            document.getElementById('auto_beta_fetch').checked = data.auto_beta;

            const count = data.keybox_count;
            const statusEl = document.getElementById('keyboxStatus');
            if (count > 0) {
                statusEl.innerText = 'Keybox Status: ACTIVE (' + count + ' keys loaded)';
                statusEl.style.color = '#fff';
            } else {
                statusEl.innerText = 'Keybox Status: INACTIVE (No keys)';
                statusEl.style.color = '#555';
            }

            const tmplSel = document.getElementById('templateSelector');
            if (data.templates) {
                data.templates.forEach(t => {
                    const opt = document.createElement('option');
                    opt.value = t;
                    opt.innerText = t;
                    tmplSel.appendChild(opt);
                });
            }

            loadFile();
        }

        async function toggle(setting) {
            const el = document.getElementById(setting);
            const val = el.checked;
            const originalOpacity = el.style.opacity;
            el.disabled = true;
            el.style.opacity = '0.5';

            try {
                const res = await fetch(getAuthUrl(baseUrl + '/toggle'), {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: 'setting=' + setting + '&value=' + val
                });
                if (res.ok) {
                    showToast('SETTING UPDATED');
                } else {
                    throw new Error('Server error');
                }
            } catch (e) {
                el.checked = !val;
                showToast('UPDATE FAILED');
            } finally {
                el.disabled = false;
                el.style.opacity = originalOpacity;
            }
        }

        async function loadFile() {
            const filename = document.getElementById('fileSelector').value;

            const tmplSection = document.getElementById('templateSection');
            if (filename === 'spoof_build_vars') {
                tmplSection.style.display = 'block';
            } else {
                tmplSection.style.display = 'none';
            }

            const res = await fetch(getAuthUrl(baseUrl + '/file?filename=' + filename));
            const text = await res.text();
            document.getElementById('editor').value = text;
        }

        function applyTemplate() {
            const tmpl = document.getElementById('templateSelector').value;
            if (!tmpl) return;
            const editor = document.getElementById('editor');
            const current = editor.value;

            if (current.includes('TEMPLATE=')) {
                if (!confirm('Replace existing TEMPLATE directive?')) return;
                editor.value = current.replace(/TEMPLATE=[^\n]*/, 'TEMPLATE=' + tmpl);
            } else {
                editor.value = 'TEMPLATE=' + tmpl + '\n' + current;
            }
        }

        async function uploadKeybox() {
            const filename = document.getElementById('kbFilename').value;
            const content = document.getElementById('kbContent').value;
            if (!filename || !content) { showToast('MISSING INFO'); return; }
            if (!filename.endsWith('.xml')) { showToast('MUST BE .XML'); return; }

            const btn = event.target;
            const originalText = btn.innerText;
            btn.disabled = true;
            btn.innerText = 'UPLOADING...';

            try {
                const params = new URLSearchParams();
                params.append('filename', filename);
                params.append('content', content);

                const res = await fetch(getAuthUrl(baseUrl + '/upload_keybox'), {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: params
                });

                if (res.ok) {
                    showToast('UPLOADED');
                    document.getElementById('kbFilename').value = '';
                    document.getElementById('kbContent').value = '';
                    setTimeout(init, 1000);
                } else {
                    const txt = await res.text();
                    showToast('FAILED: ' + txt);
                }
            } catch (e) {
                showToast('ERROR');
            } finally {
                btn.disabled = false;
                btn.innerText = originalText;
            }
        }

        async function saveFile() {
            const btn = document.getElementById('saveBtn');
            const originalText = btn.innerText;
            btn.disabled = true;
            btn.innerText = 'SAVING...';

            try {
                const filename = document.getElementById('fileSelector').value;
                const content = document.getElementById('editor').value;
                const params = new URLSearchParams();
                params.append('filename', filename);
                params.append('content', content);

                const res = await fetch(getAuthUrl(baseUrl + '/save'), {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: params
                });

                if (res.ok) showToast('SAVED');
                else showToast('SAVE FAILED');
            } catch (e) {
                showToast('ERROR');
            } finally {
                btn.disabled = false;
                btn.innerText = originalText;
            }
        }

        async function fetchBetaNow() {
            if (!confirm("Overwrite spoof_build_vars with latest Beta fingerprint?")) return;
            const btn = event.target;
            const originalText = btn.innerText;
            btn.disabled = true;
            btn.innerText = 'FETCHING...';
            
            try {
                const res = await fetch(getAuthUrl(baseUrl + '/fetch_beta'), { method: 'POST' });
                const text = await res.text();
                if (res.ok) {
                    showToast('SUCCESS: ' + text);
                    loadFile();
                } else {
                    showToast('FAILED');
                }
            } catch (e) {
                showToast('ERROR');
            } finally {
                btn.disabled = false;
                btn.innerText = originalText;
            }
        }

        async function reloadConfig() {
            const btn = document.getElementById('reloadBtn');
            const originalText = btn.innerText;
            btn.disabled = true;
            btn.innerText = 'RELOADING...';

            try {
                const res = await fetch(getAuthUrl(baseUrl + '/reload'), { method: 'POST' });
                if (res.ok) {
                    btn.innerText = 'RELOADED';
                } else {
                    btn.innerText = 'FAILED';
                }
            } catch (e) {
                btn.innerText = 'ERROR';
            }
            setTimeout(function() {
                btn.innerText = originalText;
                btn.disabled = false;
            }, 2000);
        }

        init();
    </script>
</body>
</html>
        """.trimIndent()
    }
}
