import sys

filepath = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"

with open(filepath, 'r') as f:
    content = f.read()

# 1. Update Toggle CSS
old_toggle = 'input[type="checkbox"].toggle { appearance: none; width: 40px; height: 22px; background: #333; border-radius: 20px; position: relative; cursor: pointer; transition: background 0.3s; }'
new_toggle = 'input[type="checkbox"].toggle { appearance: none; width: 52px; height: 32px; background: #333; border-radius: 16px; position: relative; cursor: pointer; transition: background 0.3s; }'

old_toggle_after = "input[type=\"checkbox\"].toggle::after { content: ''; position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; background: #fff; border-radius: 50%; transition: transform 0.3s; }"
new_toggle_after = "input[type=\"checkbox\"].toggle::after { content: ''; position: absolute; top: 3px; left: 3px; width: 26px; height: 26px; background: #fff; border-radius: 50%; transition: transform 0.3s; }"

old_toggle_checked = 'input[type="checkbox"].toggle:checked::after { transform: translateX(18px); }'
new_toggle_checked = 'input[type="checkbox"].toggle:checked::after { transform: translateX(20px); }'

content = content.replace(old_toggle, new_toggle)
content = content.replace(old_toggle_after, new_toggle_after)
content = content.replace(old_toggle_checked, new_toggle_checked)

# 2. Update Media Query and add .res-desc
# I'll replace the whole media query block
old_media = """        @media screen and (max-width: 600px) {
            #appTable thead { display: none; }
            #appTable tr { display: block; border: 1px solid var(--border); margin-bottom: 10px; border-radius: 8px; background: #1a1a1a; }
            #appTable td { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #333; padding: 12px; }
            #appTable td:last-child { border-bottom: none; justify-content: flex-end; }
            #appTable td::before { content: attr(data-label); color: #888; font-weight: 500; margin-right: 10px; }
        }"""

new_media = """        .res-desc { display: block; font-size: 0.8em; color: #888; margin-top: 4px; line-height: 1.3; }
        @media screen and (max-width: 600px) {
            .responsive-table thead { display: none; }
            .responsive-table tr { display: block; border: 1px solid var(--border); margin-bottom: 10px; border-radius: 8px; background: #1a1a1a; }
            .responsive-table td { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 1px solid #333; padding: 12px; min-height: 40px; }
            .responsive-table td:last-child { border-bottom: none; }
            .responsive-table td::before { content: attr(data-label); color: #888; font-weight: 500; margin-right: 10px; min-width: 100px; display: inline-block; }
            .responsive-table td > div, .responsive-table td > span { text-align: right; flex: 1; word-break: break-word; }
        }"""

if old_media in content:
    content = content.replace(old_media, new_media)
else:
    print("Warning: Media query block not found exactly as expected. Checking partials...")
    # If indentation is different, try to handle it.
    # The file content I read earlier shows 4 spaces indentation for the css.
    pass

# 3. Add class="responsive-table" to tables
content = content.replace('id="appTable"', 'id="appTable" class="responsive-table"')
content = content.replace('id="resourceTable"', 'id="resourceTable" class="responsive-table"')

# 4. JS Updates

# 4a. Update renderResourceTable
old_js_func_start = 'function renderResourceTable(data) {'
# We will replace the entire function body roughly.
# Locating the function in content
start_idx = content.find(old_js_func_start)
if start_idx != -1:
    end_idx = content.find('function downloadLangTemplate()', start_idx)
    if end_idx != -1:
        # Extract the function block
        func_block = content[start_idx:end_idx].strip()

        # New function implementation
        new_func = """function renderResourceTable(data) {
            const tbody = document.getElementById('resourceBody');
            if (!tbody) return;
            tbody.innerHTML = '';

            const totalRow = document.createElement('tr');
            const ramMb = (data.real_ram_kb / 1024).toFixed(2);
            const cpu = data.real_cpu ? data.real_cpu.toFixed(1) : "0.0";
            const env = data.environment || "Unknown";

            totalRow.innerHTML = '<td colspan="5" style="background:#222; font-weight:bold; padding:10px;">Env: ' + env + ' | CPU: ' + cpu + '% | RAM: ' + ramMb + ' MB</td>';
            tbody.appendChild(totalRow);

            const keyboxRam = (data.keybox_count * 0.01).toFixed(2);
            const appConfigRam = (data.app_config_size / 1024).toFixed(2);

            const features = [
                { id: 'global_mode', name: 'Global Mode', ram: '~5 MB', cpu: 'High (All Apps)', sec: 'Medium', desc: 'Hooks all apps. Disabling saves RAM but breaks global spoofing.' },
                { id: 'rkp_bypass', name: 'RKP Bypass', ram: '~2 MB', cpu: 'Medium (Crypto)', sec: 'Critical', desc: 'Required for Strong Integrity. Do not disable unless necessary.' },
                { id: 'tee_broken_mode', name: 'TEE Broken Mode', ram: 'Negligible', cpu: 'Low', sec: 'Low', desc: 'Forces software keystore behavior.' },
                { id: 'keybox_storage', name: 'Keybox Storage', ram: '~' + keyboxRam + ' MB', cpu: 'Low', sec: 'Medium', desc: data.keybox_count + ' keyboxes loaded. More keys = more RAM.' },
                { id: 'app_rules', name: 'App Rules', ram: '~' + appConfigRam + ' KB', cpu: 'Low', sec: 'Low', desc: 'Per-app configuration rules.' }
            ];

            features.forEach(f => {
                const tr = document.createElement('tr');
                const isToggleable = ['global_mode', 'rkp_bypass', 'tee_broken_mode'].includes(f.id);
                let statusHtml = '';

                if (isToggleable) {
                    const isChecked = data[f.id] ? 'checked' : '';
                    statusHtml = '<input type="checkbox" class="toggle" id="res_toggle_' + f.id + '" ' + isChecked + ' onchange="toggle(\'' + f.id + '\')">';
                } else {
                    statusHtml = '<span style="color:#888;">Info Only</span>';
                }

                let secColor = f.sec === 'Critical' ? 'var(--danger)' : (f.sec === 'High' ? 'orange' : 'var(--success)');

                // Single row layout for responsive design
                tr.innerHTML =
                    '<td data-label="' + t('col_feature') + '"><div>' + f.name + '</div><div class="res-desc">' + f.desc + '</div></td>' +
                    '<td data-label="' + t('col_status') + '">' + statusHtml + '</td>' +
                    '<td data-label="' + t('col_ram') + '" style="font-family:monospace;">' + f.ram + '</td>' +
                    '<td data-label="' + t('col_cpu') + '">' + f.cpu + '</td>' +
                    '<td data-label="' + t('col_security') + '" style="color:' + secColor + '; font-weight:bold;">' + f.sec + '</td>';

                tbody.appendChild(tr);
            });
        }
        """
        # Replace the function
        content = content[:start_idx] + new_func + "\n        " + content[end_idx:]

with open(filepath, 'w') as f:
    f.write(content)

print("Updates applied successfully.")
