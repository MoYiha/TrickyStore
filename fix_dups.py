import sys

with open('rust/cbor-cose/src/ffi.rs', 'r') as f:
    content = f.read()

# I appended rust_prop_get twice!
idx = content.find("/* ==== System Properties ==== */")
if idx != -1:
    idx2 = content.find("/* ==== System Properties ==== */", idx + 1)
    if idx2 != -1:
        # Keep only until the second one
        content = content[:idx2]

with open('rust/cbor-cose/src/ffi.rs', 'w') as f:
    f.write(content)
