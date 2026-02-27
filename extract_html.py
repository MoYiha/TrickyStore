import re

with open("service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt", "r") as f:
    content = f.read()

start_marker = 'private val htmlContent by lazy {'
start_idx = content.find(start_marker)

if start_idx != -1:
    quote_start = content.find('"""', start_idx)
    if quote_start != -1:
        quote_end = content.find('"""', quote_start + 3)
        if quote_end != -1:
            html = content[quote_start + 3 : quote_end]

            # Simple replacement
            html = html.replace('${getAppName()}', 'CleveresTricky')

            # Fix kotlin escaping for JS template literals: ${'$'} -> $
            # We must be careful because python string also uses $
            html = html.replace("${'$'}", "$")

            with open("index.html", "w") as out:
                out.write(html)
            print("HTML extracted")
        else:
            print("No closing quote")
    else:
        print("No opening quote")
else:
    print("No marker")
