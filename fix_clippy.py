import sys

with open('rust/cbor-cose/src/ffi.rs', 'r') as f:
    content = f.read()

# Add #[allow(clippy::missing_safety_doc)] before the functions
content = content.replace("pub unsafe extern \"C\" fn rust_prop_get", "#[allow(clippy::missing_safety_doc)]\npub unsafe extern \"C\" fn rust_prop_get")
content = content.replace("pub unsafe extern \"C\" fn rust_prop_set", "#[allow(clippy::missing_safety_doc)]\npub unsafe extern \"C\" fn rust_prop_set")

with open('rust/cbor-cose/src/ffi.rs', 'w') as f:
    f.write(content)
