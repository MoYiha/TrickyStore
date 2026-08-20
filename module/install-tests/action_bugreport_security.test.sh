#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
ACTION_SH="$REPO_ROOT/module/template/action.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_contains() {
  needle=$1
  grep -Fq -- "$needle" "$ACTION_SH" || fail "action.sh is missing security contract: $needle"
}

assert_absent() {
  needle=$1
  if grep -Fq -- "$needle" "$ACTION_SH"; then
    fail "action.sh restored unsafe pattern: $needle"
  fi
}

helper=$(mktemp)
trap 'rm -f "$helper"' EXIT
sed -n '/^generate_report_nonce() {$/,/^}$/p' "$ACTION_SH" > "$helper"
# shellcheck source=/dev/null
. "$helper"

nonce=$(generate_report_nonce) || fail "could not obtain a report nonce"
[[ $nonce =~ ^[0-9a-f]{32}$ ]] || fail "report nonce is not 128-bit lowercase hex"

assert_contains 'workspace="$CONFIG_DIR/.bugreport-$report_nonce"'
assert_contains 'tmp="$workspace/payload"'
assert_contains 'staged_archive="$workspace/report.tar.gz"'
assert_contains 'filename="CleveresTricky-bugreport-$stamp-$report_nonce.tar.gz"'
assert_contains 'WEBUI_BRIDGE="$MODDIR/webui_bridge"'
assert_contains '(ulimit -f "$REPORT_FILE_BLOCK_LIMIT" && create_archive "$staged_archive")'
assert_contains 'out=$("$WEBUI_BRIDGE" publish-report "$report_nonce" "$filename")'

assert_absent 'tmp="/data/local/tmp/cleverestricky-bugreport-$$"'
assert_absent 'mkdir -p "$shell_outdir"'
assert_absent 'mkdir -p "$download_dir"'
assert_absent 'create_archive "$out"'
assert_absent 'rm -f "$out"'
assert_absent 'chown 2000 "$SHELL_DIR/files"'
assert_absent 'chgrp 2000 "$SHELL_DIR/files"'
assert_absent 'cat > '\''$out'\'''

echo "bugreport archive security tests passed"
