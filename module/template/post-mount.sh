#!/system/bin/sh
MODDIR=${0%/*}

# KernelSU/APatch load module system.prop data after regular post-fs-data scripts.
# Reuse the same validated identity owner after that stage so another provider's
# property file cannot silently replace an explicitly enabled Build Identity
# before Android application processes snapshot Build.* values.
CLEVERES_TRICKY_IDENTITY_ONLY=1
export CLEVERES_TRICKY_IDENTITY_ONLY
. "$MODDIR/post-fs-data.sh"
