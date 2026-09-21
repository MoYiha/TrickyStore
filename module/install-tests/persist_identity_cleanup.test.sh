#!/usr/bin/env bash
# Regression tests for boot-time identity property hygiene in post-fs-data.sh:
# - remove_prop is idempotent (already-absent props succeed quietly instead of
#   logging a false "Failed to remove" error, matching BootLogic semantics).
# - reconcile_stale_region_persist_props clears a module-owned persist.radio
#   override after the region feature is disabled, so a stale MATCH cannot keep
#   altering modem/carrier behavior across reboots. Genuine or foreign values
#   and missing markers are never touched.
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
POST_FS_DATA="$REPO_ROOT/module/template/post-fs-data.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/cfg" "$fixture/bin" "$fixture/props"

cat > "$fixture/bin/getprop" <<'STUB'
#!/bin/bash
name=$1
path="$FIXTURE_PROPS/$name"
[ -f "$path" ] || exit 0
cat "$path"
STUB
cat > "$fixture/bin/resetprop" <<'STUB'
#!/bin/bash
echo "resetprop $*" >> "$FIXTURE_CALLS"
if [ "$1" = "-n" ]; then
  printf '%s' "$3" > "$FIXTURE_PROPS/$2"
  exit 0
fi
if [ "$1" = "--delete" ]; then
  if [ -f "$FIXTURE_PROPS/$2" ]; then
    rm -f "$FIXTURE_PROPS/$2"
    exit 0
  fi
  exit 1
fi
exit 1
STUB
chmod +x "$fixture/bin/getprop" "$fixture/bin/resetprop"

helper="$fixture/helpers.sh"
for fn in boot_policy_feature_enabled optional_marker_enabled apply_prop remove_prop mark_region_props_applied reconcile_stale_region_persist_props; do
  sed -n "/^$fn() {\$/,/^}\$/p" "$POST_FS_DATA" >> "$helper"
done
[ -s "$helper" ] || fail "could not extract post-fs-data helpers"

run_case() {
  export FIXTURE_PROPS="$fixture/props"
  export FIXTURE_CALLS="$fixture/calls.log"
  export CONFIG_DIR="$fixture/cfg"
  export CONFIG_ROOT_SAFE=true
  export PATH="$fixture/bin:$PATH"
  rm -f "$fixture/calls.log" "$fixture/messages.log"
  # shellcheck source=/dev/null
  "$BASH" -c "
    set -euo pipefail
    log() { printf '%s\n' \"\$*\" >> '$fixture/messages.log'; }
    export CONFIG_DIR CONFIG_ROOT_SAFE
    export PATH='$fixture/bin:'\"\$PATH\"
    export FIXTURE_PROPS FIXTURE_CALLS
    source '$helper'
    $1
  "
}

# 1: remove_prop on an already-absent property succeeds without touching resetprop.
rm -f "$fixture/props/sys.oem_unlock_allowed"
run_case 'remove_prop sys.oem_unlock_allowed'
[ ! -f "$fixture/calls.log" ] || fail "absent prop must not invoke resetprop"
[ ! -f "$fixture/messages.log" ] || fail "absent prop must not log an error"

# 2: remove_prop deletes a present property.
printf '0' > "$fixture/props/sys.oem_unlock_allowed"
run_case 'remove_prop sys.oem_unlock_allowed'
[ ! -f "$fixture/props/sys.oem_unlock_allowed" ] || fail "present prop was not deleted"

# 3: remove_prop reports failure only when the delete itself fails.
printf '0' > "$fixture/props/stuck.prop"
if run_case 'resetprop() { echo "resetprop $*" >> "$FIXTURE_CALLS"; return 1; }; export -f resetprop; remove_prop stuck.prop'; then
  fail "failed delete must return nonzero"
fi
grep -Fq "Failed to remove a legacy boot property: stuck.prop" "$fixture/messages.log" \
  || fail "failed delete must log the property name"
rm -f "$fixture/props/stuck.prop"

# 4: reconcile clears a stale module-owned MATCH after the region is disabled.
rm -f "$fixture/cfg/spoof_region_cn"
printf 'MATCH' > "$fixture/props/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
run_case 'reconcile_stale_region_persist_props'
grep -Fq "resetprop --delete persist.radio.skhwc_matchres" "$fixture/calls.log" \
  || fail "stale module MATCH must be deleted"
[ ! -f "$fixture/props/persist.radio.skhwc_matchres" ] || fail "stale MATCH file must be gone"
[ ! -f "$fixture/cfg/region_props_applied" ] || fail "reconciliation marker must be retired"

# 5: reconcile leaves everything alone while the region stays enabled.
: > "$fixture/cfg/spoof_region_cn"
printf 'MATCH' > "$fixture/props/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
run_case 'reconcile_stale_region_persist_props'
[ ! -f "$fixture/calls.log" ] || fail "enabled region must not trigger any resetprop call"
[ -f "$fixture/cfg/region_props_applied" ] || fail "enabled region must keep its marker"

# 6: reconcile never touches a MATCH without proof the module set it.
rm -f "$fixture/cfg/spoof_region_cn" "$fixture/cfg/region_props_applied"
printf 'MATCH' > "$fixture/props/persist.radio.skhwc_matchres"
run_case 'reconcile_stale_region_persist_props'
[ ! -f "$fixture/calls.log" ] || fail "unmarked MATCH must not be deleted"
[ -f "$fixture/props/persist.radio.skhwc_matchres" ] || fail "unmarked MATCH must be preserved"

# 7: reconcile drops the marker but preserves a foreign value.
printf 'OTHER' > "$fixture/props/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
run_case 'reconcile_stale_region_persist_props'
[ ! -f "$fixture/calls.log" ] || fail "foreign value must not be deleted"
[ -f "$fixture/props/persist.radio.skhwc_matchres" ] || fail "foreign value must be preserved"
[ ! -f "$fixture/cfg/region_props_applied" ] || fail "marker must be retired once the value is foreign"

# 8: apply path records its marker when the persist override sticks.
: > "$fixture/cfg/spoof_region_cn"
rm -f "$fixture/props/persist.radio.skhwc_matchres" "$fixture/cfg/region_props_applied"
run_case 'apply_prop persist.radio.skhwc_matchres MATCH && mark_region_props_applied'
[ -f "$fixture/cfg/region_props_applied" ] || fail "apply path must record its marker"

# 9: symlinked reconciliation marker is refused, never followed.
# (Symlink creation is unavailable on Windows/MSYS; this sub-check runs
# where real symlinks exist, matching the other installer suites.)
rm -f "$fixture/cfg/region_props_applied" "$fixture/cfg/link_probe"
ln -s "$fixture/props/persist.radio.skhwc_matchres" "$fixture/cfg/link_probe" 2>/dev/null || true
if [ ! -L "$fixture/cfg/link_probe" ]; then
  echo "SKIP: symlinks unavailable, skipping symlink refusal sub-check"
else
  rm -f "$fixture/cfg/link_probe"
  ln -s "$fixture/props/persist.radio.skhwc_matchres" "$fixture/cfg/region_props_applied"
  run_case 'mark_region_props_applied'
  [ -L "$fixture/cfg/region_props_applied" ] || fail "symlink marker must be left untouched"
fi

echo "persist identity cleanup regression tests passed"
