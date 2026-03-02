import re

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    content = f.read()

# Fix string interpolation syntax for JS inside Kotlin raw strings
content = content.replace('confirm(`Apply the ${profileName} profile?', 'confirm(`Apply the ${"$"}{profileName} profile?')
content = content.replace('notify(`Profile ${profileName} Applied`);', 'notify(`Profile ${"$"}{profileName} Applied`);')

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'w') as f:
    f.write(content)
