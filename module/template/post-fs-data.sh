MODDIR=${0%/*}
CONFIG_DIR="/data/adb/cleverestricky"

# Fix SELinux context for config directory to ensure daemon can read/write
if [ -d "$CONFIG_DIR" ]; then
  chcon -R u:object_r:cleverestricky_data_file:s0 "$CONFIG_DIR"
fi
