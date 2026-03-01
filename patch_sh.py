import sys

def main():
    path = "module/template/provision_attestation.sh"
    with open(path, "r") as f:
        data = f.read()

    new_data = data.replace('CONFIG_DIR="/data/adb/cleverestricky"\nSPOOF_VARS="$CONFIG_DIR/spoof_build_vars"', 'CONFIG_DIR="/data/adb/cleverestricky"\nSPOOF_VARS="$CONFIG_DIR/spoof_build_vars"\n\nif [ ! -f "$CONFIG_DIR/id_attest_provision" ]; then\n    exit 0\nfi\n')

    with open(path, "w") as f:
        f.write(new_data)

if __name__ == '__main__':
    main()
