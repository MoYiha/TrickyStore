import re
with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    content = f.read()

# Fix compilation using `session.parms`
content = content.replace(
    'val params = session.parameters',
    '@Suppress("DEPRECATION")\n        val params = session.parms'
)

# And fix `params["token"]?.firstOrNull()` to `params["token"]`
def fix_params(match):
    return f'params["{match.group(1)}"]'

content = re.sub(r'params\["([^"]+)"\]\?\.firstOrNull\(\)', fix_params, content)

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'w') as f:
    f.write(content)
