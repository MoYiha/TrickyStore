import sys

filepath = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"

with open(filepath, 'r') as f:
    content = f.read()

# Fix the toggle quotes issue: toggle('' + f.id + '') -> toggle(\'' + f.id + '\')
# The previous script might have messed up the escaping in the Python string literal for the Kotlin raw string.
bad_string = "toggle('' + f.id + '')"
good_string = "toggle(\'' + f.id + '\')"

if bad_string in content:
    content = content.replace(bad_string, good_string)
    print("Fixed toggle quote escaping.")
else:
    print("Toggle quote escaping seems correct or not found.")

with open(filepath, 'w') as f:
    f.write(content)
