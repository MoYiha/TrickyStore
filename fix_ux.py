import re

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    content = f.read()

# Replace <select id="srvAuthType" style="margin-bottom:5px;">
# With one that has an onchange handler to toggle auth fields
replacement = '''<select id="srvAuthType" style="margin-bottom:5px;" onchange="
                    const t = this.value;
                    const af = document.getElementById('authFields');
                    if (t === 'NONE') af.innerHTML = '';
                    else if (t === 'BEARER') af.innerHTML = '<input type=\\'text\\' id=\\'srvAuthToken\\' placeholder=\\'Bearer Token\\' style=\\'margin-bottom:5px;\\'>';
                    else if (t === 'BASIC') af.innerHTML = '<input type=\\'text\\' id=\\'srvAuthUser\\' placeholder=\\'Username\\' style=\\'margin-bottom:5px;\\'><input type=\\'password\\' id=\\'srvAuthPass\\' placeholder=\\'Password\\' style=\\'margin-bottom:5px;\\'>';
                    else if (t === 'API_KEY') af.innerHTML = '<input type=\\'text\\' id=\\'srvApiKeyName\\' placeholder=\\'Header Name (e.g. X-API-Key)\\' style=\\'margin-bottom:5px;\\'><input type=\\'password\\' id=\\'srvApiKeyValue\\' placeholder=\\'API Key\\' style=\\'margin-bottom:5px;\\'>';
                ">'''

content = content.replace('<select id="srvAuthType" style="margin-bottom:5px;">', replacement)

# Update addServer to use these fields
old_add_server = """            const authType = document.getElementById('srvAuthType').value;
            // Collect auth data based on type (simplified for now)
            const data = { name, url, authType };"""

new_add_server = """            const authType = document.getElementById('srvAuthType').value;
            const authData = {};
            if (authType === 'BEARER') authData.token = document.getElementById('srvAuthToken')?.value || '';
            else if (authType === 'BASIC') { authData.username = document.getElementById('srvAuthUser')?.value || ''; authData.password = document.getElementById('srvAuthPass')?.value || ''; }
            else if (authType === 'API_KEY') { authData.keyName = document.getElementById('srvApiKeyName')?.value || ''; authData.keyValue = document.getElementById('srvApiKeyValue')?.value || ''; }
            const data = { name, url, authType, authData };"""

content = content.replace(old_add_server, new_add_server)

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'w') as f:
    f.write(content)
