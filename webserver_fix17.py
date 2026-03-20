import re

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    content = f.read()

html_start = content.find('<!DOCTYPE html>')
html_end = content.find('</html>') + 7

html = content[html_start:html_end]

# Clean up Kotlin string interpolation
html = html.replace('${getAppName()}', 'CleveresTricky')
html = re.sub(r'\$\{BuildConfig\.DEBUG\}', 'true', html)
html = html.replace('${s.name}', 'Server Name')
html = html.replace('${s.url}', 'http://localhost')
html = html.replace('${s.lastStatus.startsWith(\'OK\')?\'OK\':\'ERROR\'}', 'OK')
html = html.replace('${s.lastStatus}', 'OK')
html = html.replace('${s.id}', 'server_id')
html = html.replace('${k}', 'keybox_name')
html = html.replace('${f}', 'file_name')
html = html.replace('${idx}', '0')
html = html.replace('${rule.package}', 'com.example.app')
html = html.replace('${rule.template === \'null\' ? \'Default\' : rule.template}', 'Default')
html = html.replace('${rule.keybox && rule.keybox !== \'null\' ? rule.keybox : \'\'}', 'keybox')
html = html.replace('${permStr}', 'PERMISSIONS')

# The javascript interpolation needs escaping
# We're just trying to test the UI, so we can mock the fetch calls
script_mock = """
        const urlParams = new URLSearchParams(window.location.search);
        const token = urlParams.get('token') || 'mock_token';

        // Mock fetchAuth
        async function fetchAuth(url, options = {}) {
            console.log('Mock fetch:', url, options);
            if (url.includes('/api/config')) {
                return { ok: true, json: async () => ({
                    global_mode: false, tee_broken_mode: false, rkp_bypass: true,
                    auto_beta_fetch: false, auto_keybox_check: true, random_on_boot: false,
                    drm_fix: false, random_drm_on_boot: false, auto_patch_update: false,
                    hide_sensitive_props: true, spoof_region_cn: false, remove_magisk_32: false,
                    spoof_location: false, imei_global: false, network_global: false,
                    keybox_count: 5
                })};
            }
            if (url.includes('/api/stats')) {
                return { ok: true, json: async () => ({ members: "10,000", banned: "500" }) };
            }
            if (url.includes('/api/templates')) {
                return { ok: true, json: async () => ([
                    {id: "pixel8pro", model: "Pixel 8 Pro", manufacturer: "Google", fingerprint: "google/husky...", securityPatch: "2024-05-05"}
                ]) };
            }
            if (url.includes('/api/packages')) {
                return { ok: true, json: async () => (["com.google.android.gms", "com.netflix.mediaclient"]) };
            }
            if (url.includes('/api/keyboxes')) {
                return { ok: true, json: async () => (["keybox1.xml", "keybox2.xml"]) };
            }
            if (url.includes('/api/cbox_status')) {
                return { ok: true, json: async () => ({ locked: [], unlocked: ["keybox1.xml"], server_status: [] }) };
            }
            if (url.includes('/api/servers')) {
                return { ok: true, json: async () => ([]) };
            }
            if (url.includes('/api/resource_usage')) {
                 return { ok: true, json: async () => ({ real_ram_kb: 500000, real_cpu: 2.5, environment: "Magisk", keybox_count: 5, app_config_size: 1024, global_mode: false, rkp_bypass: true, tee_broken_mode: false }) };
            }
            if (url.includes('/api/language')) {
                 return { ok: true, json: async () => ({}) };
            }
            if (url.includes('/api/file')) {
                 return { ok: true, text: async () => ("# Mock file content") };
            }
            if (url.includes('/api/app_config_structured')) {
                 return { ok: true, json: async () => ([]) };
            }
            return { ok: true, text: async () => ('OK'), json: async () => ({}) };
        }
        function getAuthUrl(path) { return path; }
"""

html = html.replace("const token = urlParams.get('token');", script_mock)

# Replace ${} in JS string templates
html = re.sub(r'\$\{([^}]+)\}', r'${\1}', html)

with open('test_ui.html', 'w') as f:
    f.write(html)
