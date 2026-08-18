#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
CUSTOMIZE_TEMPLATE="$REPO_ROOT/module/template/customize.sh"
fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/tmp" "$fixture/mod" "$fixture/zip"
printf 'version=Vtest\n' > "$fixture/tmp/module.prop"
marker="$fixture/verifier-executed"

cat > "$fixture/zip/verify.sh" <<EOF
#!/system/bin/sh
touch "$marker"
exit 42
EOF
# Deliberately valid-looking but incorrect. A bootstrap verifier must reject it before sourcing.
printf '%064d\n' 0 > "$fixture/zip/verify.sh.sha256"
(
  cd "$fixture/zip"
  zip -q "$fixture/module.zip" verify.sh verify.sh.sha256
)

sed \
  -e 's/@DEBUG@/false/g' \
  -e 's/@SONAME@/cleverestricky/g' \
  -e 's/@SUPPORTED_ABIS@/arm64 x64/g' \
  -e 's/@MIN_SDK@/31/g' \
  -e 's/@MAX_SDK@/37/g' \
  "$CUSTOMIZE_TEMPLATE" > "$fixture/customize.sh"

ui_print() { :; }
grep_prop() { printf 'Vtest\n'; }
abort() { exit 97; }
export -f ui_print grep_prop abort

set +e
BOOTMODE=1 \
KSU=1 \
KSU_KERNEL_VER_CODE=1 \
KSU_VER_CODE=1 \
APATCH= \
MAGISK_VER_CODE= \
ARCH=arm64 \
API=31 \
TMPDIR="$fixture/tmp" \
MODPATH="$fixture/mod" \
ZIPFILE="$fixture/module.zip" \
bash "$fixture/customize.sh" >"$fixture/stdout" 2>"$fixture/stderr"
status=$?
set -e

if [[ -e "$marker" ]]; then
  echo 'FAIL: customize.sh sourced verify.sh before validating its checksum' >&2
  exit 1
fi
if [[ $status -ne 97 ]]; then
  echo "FAIL: corrupted bootstrap verifier exited with unexpected status $status" >&2
  cat "$fixture/stderr" >&2 || true
  exit 1
fi

echo 'installer bootstrap verifier security test passed'
