import re

# Okay, `session.parms` worked perfectly. I will restore it AND ONLY it, AND fix `MessageDigest.isEqual` which is what caused the final test failure!
with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'r') as f:
    content = f.read()

content = content.replace(
    'val params = session.parameters',
    '@Suppress("DEPRECATION")\n        val params = session.parms'
)

# And now, `val tokenParam = params["token"]` is `String`.
# `authToken` is `String?`.
# In tests, `authToken` is `"testtoken"`.
# What happens?
# `!MessageDigest.isEqual(token.toByteArray(), authToken.toByteArray())`
# But in `WebServer.kt`, `token` is `val token = UUID.randomUUID().toString()`.
# Wait, I changed `val token` to `var token` in my earlier script so tests can modify it. But I reverted WebServer.kt!
# So tests could not modify `token`!
# IF I JUST CHANGE `val token` TO `var token` again, IT WILL WORK!

content = content.replace(
    'val token = UUID.randomUUID().toString()',
    'var token = UUID.randomUUID().toString()'
)

with open('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt', 'w') as f:
    f.write(content)
