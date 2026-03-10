MODDIR=${0%/*}
CONFIG_DIR="/data/adb/cleverestricky"

# Fix SELinux context for config directory to ensure daemon can read/write
if [ -d "$CONFIG_DIR" ]; then
  chcon -R u:object_r:cleverestricky_data_file:s0 "$CONFIG_DIR"
fi

# Fix SELinux context for module files to ensure they are accessible
# service.apk and libraries need to be readable by apps (LSPosed)
chcon u:object_r:cleverestricky_public_file:s0 "$MODDIR"/*.apk
chcon u:object_r:cleverestricky_public_file:s0 "$MODDIR"/*.so

# Executables need to be executable by daemon
chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/inject"
chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/daemon"

# ===== Bootloader & Verified Boot State Hiding =====
# These must run at post-fs-data (before Zygote) so that every process
# started after this point sees the spoofed values.
# The daemon's BootLogic.kt re-applies them later as a safety net, but
# the early resetprop here is essential to pass Play Integrity DEVICE check
# which queries properties before the daemon is fully running.

resetprop -n ro.boot.verifiedbootstate green
resetprop -n ro.boot.flash.locked 1
resetprop -n ro.boot.veritymode enforcing
resetprop -n ro.boot.vbmeta.device_state locked
resetprop -n ro.boot.warranty_bit 0
resetprop -n ro.warranty_bit 0
resetprop -n ro.secure 1
resetprop -n ro.debuggable 0
resetprop -n ro.force.debuggable 0
resetprop -n ro.adb.secure 1
resetprop -n ro.build.type user
resetprop -n ro.build.tags release-keys
resetprop -n ro.vendor.boot.warranty_bit 0
resetprop -n ro.vendor.warranty_bit 0
resetprop -n vendor.boot.vbmeta.device_state locked
resetprop -n vendor.boot.verifiedbootstate green
resetprop -n sys.oem_unlock_allowed 0
resetprop -n ro.secureboot.lockstate locked
resetprop -n ro.oem_unlock_supported 0

# Realme specific
resetprop -n ro.boot.realmebootstate green
resetprop -n ro.boot.realme.lockstate 1

# Bootmode hiding (recovery -> unknown)
BOOTMODE=$(resetprop ro.bootmode)
if echo "$BOOTMODE" | grep -qi "recovery"; then
  resetprop -n ro.bootmode unknown
fi
BOOTMODE2=$(resetprop ro.boot.bootmode)
if echo "$BOOTMODE2" | grep -qi "recovery"; then
  resetprop -n ro.boot.bootmode unknown
fi
BOOTMODE3=$(resetprop vendor.boot.bootmode)
if echo "$BOOTMODE3" | grep -qi "recovery"; then
  resetprop -n vendor.boot.bootmode unknown
fi

# Dynamic TEE Attestation Provisioning (Fixes CSR code 20 without Keybox)
if [ -f "$MODDIR/provision_attestation.sh" ]; then
    chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/provision_attestation.sh"
    sh "$MODDIR/provision_attestation.sh" &
fi
