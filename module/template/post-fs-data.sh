MODDIR=${0%/*}
CONFIG_DIR="/data/adb/cleverestricky"

# Fix SELinux context for config directory to ensure daemon can read/write
if [ -d "$CONFIG_DIR" ]; then
  chcon -R u:object_r:cleverestricky_data_file:s0 "$CONFIG_DIR" 2>/dev/null
fi

# Fix SELinux context for module files to ensure they are accessible
# service.apk and libraries need to be readable by apps (LSPosed)
# Use find to avoid glob expansion failure when no matching files exist
find "$MODDIR" -maxdepth 1 -name '*.apk' -exec chcon u:object_r:cleverestricky_public_file:s0 {} + 2>/dev/null
find "$MODDIR" -maxdepth 1 -name '*.so' -exec chcon u:object_r:cleverestricky_public_file:s0 {} + 2>/dev/null

# Executables need to be executable by daemon
[ -f "$MODDIR/inject" ] && chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/inject" 2>/dev/null
[ -f "$MODDIR/daemon" ] && chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/daemon" 2>/dev/null
[ -f "$MODDIR/provision_attestation.sh" ] && chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/provision_attestation.sh" 2>/dev/null

# ===== Property Hiding =====
# All property hiding (bootloader state, verified boot, debug flags, etc.)
# is handled by BootLogic.kt inside the daemon at startup.
# Shell-based resetprop commands were intentionally removed:
#   - Shell scripts are trivially scannable by detection frameworks
#   - Compiled daemon code is far harder to fingerprint
#   - The daemon applies properties immediately on launch via service.sh

# Dynamic TEE Attestation Provisioning (Fixes CSR code 20 without Keybox)
if [ -f "$MODDIR/provision_attestation.sh" ]; then
    sh "$MODDIR/provision_attestation.sh" &
fi
