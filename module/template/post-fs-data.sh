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
