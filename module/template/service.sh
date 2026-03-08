DEBUG=@DEBUG@

MODDIR=${0%/*}

cd $MODDIR

(
FAIL_COUNT=0
MAX_FAILS=5

while [ true ]; do
  chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/daemon"
  ./daemon
  EXIT_CODE=$?
  if [ $EXIT_CODE -ne 0 ]; then
    FAIL_COUNT=$((FAIL_COUNT + 1))
    log -t CleveresTricky "Daemon exited with code $EXIT_CODE (attempt $FAIL_COUNT/$MAX_FAILS)"
    if [ $FAIL_COUNT -ge $MAX_FAILS ]; then
      log -t CleveresTricky "Max retries reached, giving up"
      exit 1
    fi
    sleep 10
  else
    FAIL_COUNT=0
  fi
done
) &
