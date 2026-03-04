import sys

with open('rust/cbor-cose/src/lib.rs', 'r') as f:
    content = f.read()

content = content.replace("pub mod utils;\n", "pub mod utils;\npub mod properties;\n")

with open('rust/cbor-cose/src/lib.rs', 'w') as f:
    f.write(content)
