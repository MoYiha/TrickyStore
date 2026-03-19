import os

file_path = 'service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt'

with open(file_path, 'r') as f:
    content = f.read()

# Replacement 1: Add search bar to Stored Keyboxes HTML
old_html = """        <div class="panel">
            <h3>Stored Keyboxes</h3>
            <div id="storedKeyboxesList" style="max-height: 200px; overflow-y: auto;"></div>
        </div>"""

new_html = """        <div class="panel">
            <h3>Stored Keyboxes</h3>
            <input type="search" id="keyboxFilter" placeholder="Filter keyboxes..." oninput="renderKeyboxes()" style="width:100%; margin-bottom:10px;" aria-label="Filter keyboxes">
            <div id="storedKeyboxesList" style="max-height: 200px; overflow-y: auto;"></div>
        </div>"""

if old_html in content:
    content = content.replace(old_html, new_html)
    print("Successfully replaced HTML section.")
else:
    print("Failed to find old HTML section.")

# Replacement 2: Update loadKeyboxes() JS
old_js = """        async function loadKeyboxes() {
            try {
                const res = await fetchAuth('/api/keyboxes');
                if (res.ok) {
                    const keys = await res.json();
                    const list = document.getElementById('storedKeyboxesList');
                    list.innerHTML = '';
                    keys.forEach(k => {
                        const div = document.createElement('div'); div.className = 'row'; div.style.padding = '10px'; div.style.borderBottom = '1px solid var(--border)';
                        div.innerHTML = `<span>${'$'}{k}</span><div><span style="font-size:0.8em; color:#666; margin-right:15px;">Stored</span><button class="danger" style="padding:4px 8px; font-size:0.8em;" onclick="requireConfirm(this, () => deleteKeybox('${'$'}{k}'), 'Confirm Delete')" title="Delete Keybox" aria-label="Delete ${'$'}{k}">Delete</button></div>`;
                        list.appendChild(div);
                    });
                    const dl = document.getElementById('keyboxList');
                    if (dl) { dl.innerHTML = ''; keys.forEach(k => { const opt = document.createElement('option'); opt.value = k; dl.appendChild(opt); }); }
                }
            } catch(e) {}
        }"""

new_js = """        let cachedKeyboxes = [];
        async function loadKeyboxes() {
            try {
                const res = await fetchAuth('/api/keyboxes');
                if (res.ok) {
                    cachedKeyboxes = await res.json();
                    renderKeyboxes();
                    const dl = document.getElementById('keyboxList');
                    if (dl) { dl.innerHTML = ''; cachedKeyboxes.forEach(k => { const opt = document.createElement('option'); opt.value = k; dl.appendChild(opt); }); }
                }
            } catch(e) {}
        }

        function renderKeyboxes() {
            const list = document.getElementById('storedKeyboxesList');
            const filterInput = document.getElementById('keyboxFilter');
            const filterText = filterInput ? filterInput.value.toLowerCase() : '';
            if (!list) return;
            list.innerHTML = '';
            let matchCount = 0;

            cachedKeyboxes.forEach(k => {
                if (filterText && !k.toLowerCase().includes(filterText)) return;
                matchCount++;
                const div = document.createElement('div'); div.className = 'row'; div.style.padding = '10px'; div.style.borderBottom = '1px solid var(--border)';
                div.innerHTML = `<span>${'$'}{k}</span><div><span style="font-size:0.8em; color:#666; margin-right:15px;">Stored</span><button class="danger" style="padding:4px 8px; font-size:0.8em;" onclick="requireConfirm(this, () => deleteKeybox('${'$'}{k}'), 'Confirm Delete')" title="Delete Keybox" aria-label="Delete ${'$'}{k}">Delete</button></div>`;
                list.appendChild(div);
            });

            if (filterText && matchCount === 0) {
                 const div = document.createElement('div');
                 div.style.padding = '10px'; div.style.textAlign = 'center'; div.style.color = '#666';
                 div.innerHTML = 'No keyboxes match your filter. <button onclick="document.getElementById(\\'keyboxFilter\\').value=\\'\\'; renderKeyboxes()" style="margin-left:10px; padding:4px 8px; font-size:0.85em;">Clear Filter</button>';
                 list.appendChild(div);
            } else if (cachedKeyboxes.length === 0) {
                 const div = document.createElement('div');
                 div.style.padding = '10px'; div.style.textAlign = 'center'; div.style.color = '#666';
                 div.innerText = 'No keyboxes stored.';
                 list.appendChild(div);
            }
        }"""

if old_js in content:
    content = content.replace(old_js, new_js)
    print("Successfully replaced JS section.")
else:
    print("Failed to find old JS section.")

# Replace missing spelling attributes in other input fields (e.g. Server UI)
# We already have spellcheck="false" autocomplete="off" autocorrect="off" autocapitalize="off" on appPkg, appKeybox, kbContent, kbFilenameInput, fileEditor
old_add_server = '<input type="text" id="srvName" placeholder="Name" style="margin-bottom:5px;">'
new_add_server = '<input type="text" id="srvName" placeholder="Name" style="margin-bottom:5px;" spellcheck="false" autocomplete="off" autocorrect="off" autocapitalize="off">'

old_add_server_url = '<input type="text" id="srvUrl" placeholder="URL (HTTPS)" style="margin-bottom:5px;">'
new_add_server_url = '<input type="text" id="srvUrl" placeholder="URL (HTTPS)" style="margin-bottom:5px;" spellcheck="false" autocomplete="off" autocorrect="off" autocapitalize="off">'

if old_add_server in content:
    content = content.replace(old_add_server, new_add_server)
    print("Replaced srvName attributes")
if old_add_server_url in content:
    content = content.replace(old_add_server_url, new_add_server_url)
    print("Replaced srvUrl attributes")

with open(file_path, 'w') as f:
    f.write(content)
