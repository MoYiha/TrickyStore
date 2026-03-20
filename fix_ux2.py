import re

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    content = f.read()

# Since I already ran a fix_ux.py script previously, let's verify if the changes are present
if '<select id="srvAuthType" style="margin-bottom:5px;" onchange="' in content:
    print("Change 1 is already present.")

if "authData.token = document.getElementById('srvAuthToken')?.value" in content:
    print("Change 2 is already present.")
