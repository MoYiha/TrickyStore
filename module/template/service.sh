DEBUG=@DEBUG@

MODDIR=${0%/*}

cd $MODDIR

(
while [ true ]; do
  chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/daemon"
  ./daemon
  if [ $? -ne 0 ]; then
    exit 1
  fi
done
) &
