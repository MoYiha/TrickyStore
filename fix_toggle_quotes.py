filepath = "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt"
with open(filepath, 'r') as f:
    content = f.read()

# Current incorrect line snippet from grep:
# onchange="toggle('' + f.id + '')"
# We want:
# onchange="toggle(\' + f.id + \')"  <-- escaped backslash for Kotlin string

# Python string representation of what's in the file:
bad = "onchange=\"toggle('' + f.id + '')\""
# What we want in the file (Kotlin raw string):
good = "onchange=\"toggle(\\' + f.id + \\')\""
# Wait, in Kotlin raw string , backslash is literal.
# So we want  in the file content.
# In Python string literal,  produces .

good_python = "onchange=\"toggle(\\' + f.id + \\')\""

if bad in content:
    content = content.replace(bad, good_python)
    print("Fixed toggle quotes.")
else:
    print("Could not find exact bad string. Trying partial match.")
    # Fallback to search just the toggle part
    bad_part = "toggle('' + f.id + '')"
    good_part = "toggle(\\' + f.id + \\')"
    if bad_part in content:
        content = content.replace(bad_part, good_part)
        print("Fixed partial toggle quotes.")
    else:
        print("Still couldn't find it.")

with open(filepath, 'w') as f:
    f.write(content)
