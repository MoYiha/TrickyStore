#!/system/bin/sh
MODDIR=${0%/*}

CLEVERES_TRICKY_IDENTITY_ONLY=1
export CLEVERES_TRICKY_IDENTITY_ONLY
# shellcheck disable=SC1090,SC1091
. "$MODDIR/post-fs-data.sh"
