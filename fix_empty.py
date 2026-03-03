import sys

with open('rust/cbor-cose/src/ffi.rs', 'r') as f:
    content = f.read()

content = content.replace("RustBuffer::empty() })", "RustBuffer::empty())")

with open('rust/cbor-cose/src/ffi.rs', 'w') as f:
    f.write(content)
