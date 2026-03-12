import re

file_path = 'service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt'

with open(file_path, 'r') as f:
    content = f.read()

# To reliably remove the whole firstParam extension function
# which is known to be:
#    private fun Map<String, *>.firstParam(name: String): String? {
#        val value = this[name] ?: return null
#        return when (value) {
#            is String -> value
#            is List<*> -> value.firstOrNull()?.toString()
#            else -> value.toString()
#        }
#    }
# We use a specific regex that matches the exact structure.
# Or better yet, a string replacement if it is exactly as formatted.

exact_func = """
    private fun Map<String, *>.firstParam(name: String): String? {
        val value = this[name] ?: return null
        return when (value) {
            is String -> value
            is List<*> -> value.firstOrNull()?.toString()
            else -> value.toString()
        }
    }
"""

content = content.replace(exact_func, "")

# Some whitespace might be different, let's also try a more robust regex:
# Match 'private fun Map<String, *>.firstParam(name: String): String? {' up to the matching closing brace.
# Since we know the internal structure has one 'when' block with its own {}, we can match it:
regex_func = r'\s+private fun Map<String, \*>\.firstParam\(name: String\): String\? \{(?:[^{}]*\{[^{}]*\}[^{}]*)*\}'
content = re.sub(regex_func, '', content)

# Replace the getParam method to use session.parms directly
content = content.replace(
    '@Suppress("DEPRECATION")\n    private fun getParam(session: IHTTPSession, name: String): String? {\n        return session.parameters.firstParam(name) ?: session.parms[name]\n    }',
    '@Suppress("DEPRECATION")\n    private fun getParam(session: IHTTPSession, name: String): String? {\n        return session.parms[name]\n    }'
)

# Replace any occurrence where firstParam was already removed but getParam has the old return
content = re.sub(
    r'@Suppress\("DEPRECATION"\)\s*private fun getParam\(session: IHTTPSession, name: String\): String\? \{\s*return session\.parameters\.firstParam\(name\) \?: session\.parms\[name\]\s*\}',
    '@Suppress("DEPRECATION")\n    private fun getParam(session: IHTTPSession, name: String): String? {\n        return session.parms[name]\n    }',
    content
)

# Also fix the initial regex replacement if the file was modified in other ways in the user's issue
def fix_params(match):
    return f'params["{match.group(1)}"]'

content = re.sub(r'params\["([^"]+)"\]\?\.firstOrNull\(\)', fix_params, content)

with open(file_path, 'w') as f:
    f.write(content)
