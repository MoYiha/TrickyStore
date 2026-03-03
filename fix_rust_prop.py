import sys

with open('rust/cbor-cose/src/ffi.rs', 'r') as f:
    content = f.read()

content = content.replace("RustBuffer::empty()", "RustBuffer::empty()") # Check if empty() exists. Wait, earlier grep showed it exists.
