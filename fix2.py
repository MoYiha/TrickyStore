import re

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    content = f.read()

# Add API endpoint
api_endpoint = """
        if (uri == "/api/apply_profile" && method == Method.POST) {
             val map = HashMap<String, String>()
             try { session.parseBody(map) } catch(e:Exception){}
             val profileName = session.parms["profile"]
             if (profileName != null) {
                 synchronized(fileLock) {
                     try {
                         SecureFile.writeText(File(configDir, "apply_profile"), profileName)
                         return secureResponse(Response.Status.OK, "text/plain", "Profile Applied")
                     } catch (e: Exception) {
                         Logger.e("Failed to apply profile via file", e)
                         return secureResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Failed")
                     }
                 }
             }
             return secureResponse(Response.Status.BAD_REQUEST, "text/plain", "Missing profile")
        }
"""

content = content.replace(
    'if (uri == "/api/toggle" && method == Method.POST) {',
    api_endpoint + '\n        if (uri == "/api/toggle" && method == Method.POST) {'
)

# Modify WebUI Dashboard (HTML)
html_dashboard = """
        <div class="panel">
            <h3>Quick Profile</h3>
            <div class="row">
                <select id="profileSelect" onchange="applyProfile(this.value)" style="width: 100%;">
                    <option value="">Select a Profile...</option>
                    <option value="GodProfile">God Mode (Max Spoofing)</option>
                    <option value="DailyUse">Daily Use (Standard Spoofing)</option>
                    <option value="Minimal">Minimal (Clean state)</option>
                </select>
            </div>
            <div style="font-size:0.8em; color:#888; margin-top:5px;">Applying a profile will overwrite current settings below.</div>
        </div>
        <div class="panel">
"""
content = content.replace(
    '<div class="panel">\n            <h3>System Control</h3>',
    html_dashboard + '            <h3>System Control</h3>'
)

# Update WebUI JavaScript (determineActiveProfile & applyProfile)
js_additions = """
        async function applyProfile(profileName) {
            if (!profileName) return;
            if (!confirm(`Apply the ${profileName} profile? This will change your current settings.`)) {
                // Reset select if cancelled
                const res = await fetchAuth(getAuthUrl('/api/config'));
                const data = await res.json();
                determineActiveProfile(data);
                return;
            }
            try {
                const formData = new URLSearchParams();
                formData.append('profile', profileName);
                const res = await fetchAuth('/api/apply_profile', { method: 'POST', body: formData });
                if (res.ok) {
                    notify(`Profile ${profileName} Applied`);
                    setTimeout(() => window.location.reload(), 1000);
                } else {
                    notify('Failed to apply profile', 'error');
                }
            } catch (e) {
                notify('Error', 'error');
            }
        }

        function determineActiveProfile(data) {
            const isGod = data.global_mode && data.rkp_bypass && !data.tee_broken_mode && data.random_on_boot && data.hide_sensitive_props && data.drm_fix;
            const isDaily = !data.global_mode && data.rkp_bypass && !data.tee_broken_mode && !data.random_on_boot && data.hide_sensitive_props && !data.drm_fix;
            const isMinimal = !data.global_mode && !data.rkp_bypass && !data.tee_broken_mode && !data.random_on_boot && !data.hide_sensitive_props && !data.drm_fix;

            const select = document.getElementById('profileSelect');
            if (!select) return;
            if (isGod) select.value = 'GodProfile';
            else if (isDaily) select.value = 'DailyUse';
            else if (isMinimal) select.value = 'Minimal';
            else select.value = '';
        }
"""

content = content.replace(
    'async function reloadConfig() {',
    js_additions + '\n        async function reloadConfig() {'
)

# Call determineActiveProfile in init()
content = content.replace(
    'if(document.getElementById(k)) document.getElementById(k).checked = data[k];\n                });',
    'if(document.getElementById(k)) document.getElementById(k).checked = data[k];\n                });\n                determineActiveProfile(data);'
)

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'w') as f:
    f.write(content)
