#!/system/bin/sh
MODDIR=${0%/*}

(
retry_delay=2
max_retry_delay=60
stable_runtime=120

module_stopping() {
  [ -e "$MODDIR/disable" ] || [ -e "$MODDIR/remove" ]
}

while true; do
  if module_stopping; then
    log -t CleveresTricky "Module disabled or pending removal; daemon supervisor stopped"
    break
  fi
  if [ ! -x "$MODDIR/daemon" ]; then
    log -t CleveresTricky "Daemon executable is unavailable; daemon supervisor stopped"
    break
  fi

  chcon u:object_r:system_file:s0 "$MODDIR/daemon" 2>/dev/null
  chcon u:object_r:system_file:s0 "$MODDIR/inject" 2>/dev/null
  find "$MODDIR" -maxdepth 1 -type f \( -name '*.apk' -o -name '*.so' \) \
    -exec chcon u:object_r:system_file:s0 {} + 2>/dev/null

  started_at=$(date +%s)
  "$MODDIR/daemon"
  exit_code=$?
  stopped_at=$(date +%s)
  runtime=$((stopped_at - started_at))

  if [ "$runtime" -ge "$stable_runtime" ]; then
    retry_delay=2
  fi

  if module_stopping; then
    log -t CleveresTricky "Module disabled or pending removal after daemon exit; supervisor stopped"
    break
  fi

  log -t CleveresTricky \
    "Daemon exited with code $exit_code after ${runtime}s; retrying in ${retry_delay}s"
  sleep "$retry_delay"

  if [ "$runtime" -lt "$stable_runtime" ] && [ "$retry_delay" -lt "$max_retry_delay" ]; then
    retry_delay=$((retry_delay * 2))
    [ "$retry_delay" -gt "$max_retry_delay" ] && retry_delay=$max_retry_delay
  fi
done
) &
