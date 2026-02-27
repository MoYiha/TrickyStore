import os

with open("service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt", "r") as f:
    content = f.read()

marker = 'private val htmlContent by lazy {'
start_pos = content.find(marker)
if start_pos != -1:
    quote_start = content.find('"""', start_pos) + 3
    quote_end = content.find('"""', quote_start)
    html = content[quote_start:quote_end]
    html = html.replace('${getAppName()}', 'CleveresTricky')
    html = html.replace("${'$'}", "$")

    with open("verification/index.html", "w") as f:
        f.write(html)
    print("HTML extracted")
else:
    print("HTML not found")
