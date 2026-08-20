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
test_root=$(mktemp -d)
trap 'rm -f "$helper"; rm -rf "$test_root"' EXIT
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
assert_contains 'REPORT_COPY_FILE_LIMIT=128'
assert_contains 'REPORT_LOG_FILE_BLOCK_LIMIT=8192'
assert_contains 'find "$copy_src" -xdev -type f'
assert_contains '"$WEBUI_BRIDGE" copy-report-file "$report_nonce" "$1" "$2" "$3"'
assert_contains 'write_bounded_log "$tmp/logcat-all.log" logcat -b all -d -v threadtime'
assert_contains 'write_bounded_log "$tmp/dmesg.log" dmesg'

assert_absent 'tmp="/data/local/tmp/cleverestricky-bugreport-$$"'
assert_absent 'mkdir -p "$shell_outdir"'
assert_absent 'mkdir -p "$download_dir"'
assert_absent 'create_archive "$out"'
assert_absent 'rm -f "$out"'
assert_absent 'chown 2000 "$SHELL_DIR/files"'
assert_absent 'chgrp 2000 "$SHELL_DIR/files"'
assert_absent 'cat > '\''$out'\'''
assert_absent 'cp -a "$copy_src"'
assert_absent 'dd if="$report_file"'

sed -n '/^copy_report_path() {$/,/^}$/p' "$ACTION_SH" > "$helper"
sed -n '/^write_bounded_log() {$/,/^}$/p' "$ACTION_SH" >> "$helper"
# shellcheck source=/dev/null
. "$helper"

print_log() { :; }
message() { printf '%s' "$1"; }
workspace="$test_root/workspace"
tmp="$workspace/payload"
report_nonce=0123456789abcdef0123456789abcdef
mkdir -p "$tmp" "$test_root/source/nested" "$test_root/second"
printf '0123456789abcdef' > "$test_root/source/one.log"
printf 'abcdefghijklmnop' > "$test_root/source/nested/two.log"
printf 'ABCDEFGHIJKLMNOP' > "$test_root/source/three.log"
printf 'must-not-be-copied' > "$test_root/second/four.log"

REPORT_COPY_FILE_LIMIT=2
copy_report_file() {
  source_root=$1
  source_relative_path=$2
  destination_relative_path=$3
  destination="$tmp/$destination_relative_path"
  mkdir -p "${destination%/*}"
  dd if="$source_root/$source_relative_path" of="$destination" bs=4 count=2 2>/dev/null
}
report_copy_count=0
copy_report_path "$test_root/source" bounded
[ "$report_copy_count" -eq 2 ] || fail "collection did not enforce the total file-count limit"
[ -d "$tmp/bounded/source" ] || fail "collection did not preserve the source directory label"
[ "$(find "$tmp/bounded" -type f | wc -l)" -eq 2 ] || fail "collection copied too many files"
while IFS= read -r copied_file; do
  [ "$(wc -c < "$copied_file")" -le 8 ] || fail "collection copied more than the per-file byte limit"
done < <(find "$tmp/bounded" -type f)
copy_report_path "$test_root/second" bounded
[ -z "$(find "$tmp/bounded" -name four.log -print -quit)" ] || fail "collection ignored its cross-source file-count limit"

rm -rf "$tmp/bounded"
mkdir -p "$tmp"
REPORT_COPY_FILE_LIMIT=4
report_copy_count=0
ln -s "$test_root/second/four.log" "$test_root/source/linked.log"
copy_report_path "$test_root/source" regular-only
[ -z "$(find "$tmp/regular-only" -name linked.log -print -quit)" ] || fail "collection copied a symbolic link"

REPORT_LOG_FILE_BLOCK_LIMIT=4
if write_bounded_log "$tmp/bounded-command.log" dd if=/dev/zero bs=1024 count=32 2>/dev/null; then
  fail "bounded command log unexpectedly accepted oversized output"
fi
[ "$(wc -c < "$tmp/bounded-command.log")" -le 4096 ] || fail "command log exceeded its ulimit bound"

echo "bugreport archive security tests passed"
