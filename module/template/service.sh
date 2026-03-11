DEBUG=@DEBUG@

MODDIR=${0%/*}
CONFIG_DIR="/data/adb/cleverestricky"

cd $MODDIR

(
FAIL_COUNT=0
MAX_FAILS=5
BACKOFF_COUNT=0
MAX_BACKOFF_CYCLES=3
# Five minutes avoids repeated boot-time restart storms while still allowing
# the daemon to recover later if the underlying failure is transient.
BACKOFF_SECONDS=300

while [ true ]; do
  # Auto-repair SELinux contexts before each daemon launch.
  # This makes the module self-healing: if contexts were lost (e.g. after
  # an OTA or module update), the daemon can still start and inject.
  chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/daemon" 2>/dev/null
  chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/inject" 2>/dev/null
  find "$MODDIR" -maxdepth 1 -name '*.apk' -exec chcon u:object_r:cleverestricky_public_file:s0 {} + 2>/dev/null
  find "$MODDIR" -maxdepth 1 -name '*.so' -exec chcon u:object_r:cleverestricky_public_file:s0 {} + 2>/dev/null
  [ -d "$CONFIG_DIR" ] && chcon -R u:object_r:cleverestricky_data_file:s0 "$CONFIG_DIR" 2>/dev/null

  ./daemon
  EXIT_CODE=$?
  if [ $EXIT_CODE -ne 0 ]; then
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log -t CleveresTricky "Daemon exited with code $EXIT_CODE (attempt $FAIL_COUNT/$MAX_FAILS)"
    if [ $FAIL_COUNT -ge $MAX_FAILS ]; then
      BACKOFF_COUNT=$((BACKOFF_COUNT + 1))
      if [ $BACKOFF_COUNT -ge $MAX_BACKOFF_CYCLES ]; then
        log -t CleveresTricky "Exhausted $MAX_BACKOFF_CYCLES backoff cycles ($((MAX_FAILS * MAX_BACKOFF_CYCLES)) total attempts). Giving up."
        break
      fi
      log -t CleveresTricky "Max retries reached, backing off for $BACKOFF_SECONDS seconds (backoff $BACKOFF_COUNT/$MAX_BACKOFF_CYCLES)"
      sleep "$BACKOFF_SECONDS"
      FAIL_COUNT=0
      continue
    fi
    sleep 10
  else
    FAIL_COUNT=0
    BACKOFF_COUNT=0
  fi
done
) &
