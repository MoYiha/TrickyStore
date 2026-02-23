import sys

try:
    with open('webserver_content.txt', 'r') as f:
        lines = f.readlines()

    last_brace_index = -1
    companion_index = -1

    for i, line in enumerate(lines):
        if "companion object" in line:
            companion_index = i
            break

    # If companion object exists, insert before it
    if companion_index != -1:
        insert_line = companion_index
        print(f"Insert at line {insert_line}")
    else:
        # Find the last closing brace of the class
        # This is tricky without parsing, but assuming standard formatting...
        # We'll look for the last '}' at indentation level 0 or 1
        # Actually, let's just put it before "private val htmlContent" if we can find it
        # Or before "companion object"
        pass

    # Better approach: Look for "private val htmlContent"
    for i, line in enumerate(lines):
        if "private val htmlContent by lazy" in line:
             # Insert BEFORE this line
             print(f"Insert at line {i}")
             break

except Exception as e:
    print(f"Error: {e}")
