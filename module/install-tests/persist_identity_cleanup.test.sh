#!/usr/bin/env bash
# Regression tests for boot-time identity property hygiene in post-fs-data.sh:
# - remove_prop is idempotent (already-absent props succeed quietly instead of
#   logging a false "Failed to remove" error, matching BootLogic semantics).
# - reconcile_stale_region_persist_props clears a module-owned persist.radio
#   override after the region feature is disabled, so a stale MATCH cannot keep
#   altering modem/carrier behavior across reboots. Genuine or foreign values
#   and missing markers are never touched. Persistent backing storage is
#   removed with resetprop -p (falling back to a runtime-only delete that
#   keeps the marker for retry where -p is unsupported), and a failed getprop
#   read preserves the marker instead of dropping cleanup.
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
POST_FS_DATA="$REPO_ROOT/module/template/post-fs-data.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

fixture=$(mktemp -d)
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/cfg" "$fixture/bin" "$fixture/props/rt" "$fixture/props/persist"

# Dual-store stubs model Magisk resetprop semantics: plain getprop reads the
# runtime store, -n sets write both stores for persist.* names, plain --delete
# clears only the runtime store, and -p --delete clears both stores.
cat > "$fixture/bin/getprop" <<'STUB'
#!/bin/bash
[ -n "${FIXTURE_GETPROP_FAIL:-}" ] && exit 1
name=$1
path="$FIXTURE_PROPS/rt/$name"
[ -f "$path" ] || exit 0
cat "$path"
STUB
cat > "$fixture/bin/resetprop" <<'STUB'
#!/bin/bash
echo "resetprop $*" >> "$FIXTURE_CALLS"
pflag=0
if [ "$1" = "-p" ]; then
  [ -z "${FIXTURE_NO_PFLAG:-}" ] || exit 2
  shift
  pflag=1
fi
if [ "$1" = "-n" ]; then
  printf '%s' "$3" > "$FIXTURE_PROPS/rt/$2"
  case "$2" in
    persist.*) printf '%s' "$3" > "$FIXTURE_PROPS/persist/$2" ;;
  esac
  exit 0
fi
if [ "$1" = "--delete" ]; then
  if [ "$pflag" = "1" ]; then
    existed=0
    [ -f "$FIXTURE_PROPS/rt/$2" ] && existed=1
    [ -f "$FIXTURE_PROPS/persist/$2" ] && existed=1
    rm -f "$FIXTURE_PROPS/rt/$2" "$FIXTURE_PROPS/persist/$2"
    [ "$existed" = "1" ]
    exit $?
  fi
  if [ -f "$FIXTURE_PROPS/rt/$2" ]; then
    rm -f "$FIXTURE_PROPS/rt/$2"
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
    export FIXTURE_GETPROP_FAIL FIXTURE_NO_PFLAG
    source '$helper'
    $1
  "
}

# Simulate an init reboot: runtime props are repopulated from persistent storage.
simulate_reboot() {
  rm -rf "$fixture/props/rt"
  mkdir -p "$fixture/props/rt"
  for stored in "$fixture/props/persist/"*; do
    [ -f "$stored" ] || continue
    cp "$stored" "$fixture/props/rt/$(basename "$stored")"
  done
}

reset_stores() {
  rm -rf "$fixture/props/rt" "$fixture/props/persist"
  mkdir -p "$fixture/props/rt" "$fixture/props/persist"
  unset FIXTURE_GETPROP_FAIL FIXTURE_NO_PFLAG || true
}

# 1: remove_prop on an already-absent property succeeds without touching resetprop.
reset_stores
run_case 'remove_prop sys.oem_unlock_allowed'
[ ! -f "$fixture/calls.log" ] || fail "absent prop must not invoke resetprop"
[ ! -f "$fixture/messages.log" ] || fail "absent prop must not log an error"

# 2: remove_prop deletes a present property.
reset_stores
printf '0' > "$fixture/props/rt/sys.oem_unlock_allowed"
run_case 'remove_prop sys.oem_unlock_allowed'
[ ! -f "$fixture/props/rt/sys.oem_unlock_allowed" ] || fail "present prop was not deleted"

# 3: remove_prop reports failure only when the delete itself fails.
reset_stores
printf '0' > "$fixture/props/rt/stuck.prop"
if run_case 'resetprop() { echo "resetprop $*" >> "$FIXTURE_CALLS"; return 1; }; export -f resetprop; remove_prop stuck.prop'; then
  fail "failed delete must return nonzero"
fi
grep -Fq "Failed to remove a legacy boot property: stuck.prop" "$fixture/messages.log" \
  || fail "failed delete must log the property name"

# 4: reconcile clears runtime and persistent MATCH after the region is disabled,
#    and the value stays gone across a simulated reboot.
reset_stores
rm -f "$fixture/cfg/spoof_region_cn"
printf 'MATCH' > "$fixture/props/rt/persist.radio.skhwc_matchres"
printf 'MATCH' > "$fixture/props/persist/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
run_case 'reconcile_stale_region_persist_props'
grep -Fq "resetprop -p --delete persist.radio.skhwc_matchres" "$fixture/calls.log" \
  || fail "stale module MATCH must be deleted with persistent backing"
[ ! -f "$fixture/props/rt/persist.radio.skhwc_matchres" ] || fail "runtime MATCH must be gone"
[ ! -f "$fixture/props/persist/persist.radio.skhwc_matchres" ] || fail "persistent MATCH must be gone"
[ ! -f "$fixture/cfg/region_props_applied" ] || fail "reconciliation marker must be retired"
simulate_reboot
[ ! -f "$fixture/props/rt/persist.radio.skhwc_matchres" ] || fail "MATCH must not return after reboot"

# 5: reconcile leaves everything alone while the region stays enabled.
reset_stores
: > "$fixture/cfg/spoof_region_cn"
printf 'MATCH' > "$fixture/props/rt/persist.radio.skhwc_matchres"
printf 'MATCH' > "$fixture/props/persist/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
run_case 'reconcile_stale_region_persist_props'
[ ! -f "$fixture/calls.log" ] || fail "enabled region must not trigger any resetprop call"
[ -f "$fixture/cfg/region_props_applied" ] || fail "enabled region must keep its marker"

# 6: reconcile never touches a MATCH without proof the module set it.
reset_stores
rm -f "$fixture/cfg/spoof_region_cn" "$fixture/cfg/region_props_applied"
printf 'MATCH' > "$fixture/props/rt/persist.radio.skhwc_matchres"
printf 'MATCH' > "$fixture/props/persist/persist.radio.skhwc_matchres"
run_case 'reconcile_stale_region_persist_props'
[ ! -f "$fixture/calls.log" ] || fail "unmarked MATCH must not be deleted"
[ -f "$fixture/props/rt/persist.radio.skhwc_matchres" ] || fail "unmarked MATCH must be preserved"

# 7: reconcile drops the marker but preserves a foreign value.
reset_stores
printf 'OTHER' > "$fixture/props/rt/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
run_case 'reconcile_stale_region_persist_props'
[ ! -f "$fixture/calls.log" ] || fail "foreign value must not be deleted"
[ -f "$fixture/props/rt/persist.radio.skhwc_matchres" ] || fail "foreign value must be preserved"
[ ! -f "$fixture/cfg/region_props_applied" ] || fail "marker must be retired once the value is foreign"

# 8: apply path records its marker when the persist override sticks.
reset_stores
: > "$fixture/cfg/spoof_region_cn"
rm -f "$fixture/props/rt/persist.radio.skhwc_matchres" "$fixture/props/persist/persist.radio.skhwc_matchres" "$fixture/cfg/region_props_applied"
run_case 'apply_prop persist.radio.skhwc_matchres MATCH && mark_region_props_applied'
[ -f "$fixture/cfg/region_props_applied" ] || fail "apply path must record its marker"

# 9: a failed getprop read preserves the marker for retry instead of dropping cleanup.
reset_stores
rm -f "$fixture/cfg/spoof_region_cn"
printf 'MATCH' > "$fixture/props/rt/persist.radio.skhwc_matchres"
printf 'MATCH' > "$fixture/props/persist/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
export FIXTURE_GETPROP_FAIL=1
run_case 'reconcile_stale_region_persist_props'
unset FIXTURE_GETPROP_FAIL
[ ! -f "$fixture/calls.log" ] || fail "failed read must not attempt any delete"
[ -f "$fixture/cfg/region_props_applied" ] || fail "failed read must preserve the marker for retry"

# 10: without -p support the runtime is still cleared and the marker is kept.
reset_stores
rm -f "$fixture/cfg/spoof_region_cn"
printf 'MATCH' > "$fixture/props/rt/persist.radio.skhwc_matchres"
printf 'MATCH' > "$fixture/props/persist/persist.radio.skhwc_matchres"
: > "$fixture/cfg/region_props_applied"
export FIXTURE_NO_PFLAG=1
run_case 'reconcile_stale_region_persist_props'
unset FIXTURE_NO_PFLAG
grep -Fq "resetprop -p --delete persist.radio.skhwc_matchres" "$fixture/calls.log" \
  || fail "persistent delete must be attempted first"
grep -Fq "resetprop --delete persist.radio.skhwc_matchres" "$fixture/calls.log" \
  || fail "fallback runtime delete must run without -p support"
[ ! -f "$fixture/props/rt/persist.radio.skhwc_matchres" ] || fail "runtime MATCH must be cleared"
[ -f "$fixture/cfg/region_props_applied" ] || fail "marker must be kept for retry without -p support"
simulate_reboot
[ -f "$fixture/props/rt/persist.radio.skhwc_matchres" ] || fail "persistent MATCH must restore runtime after reboot"

# 11: symlinked reconciliation marker is refused, never followed.
# (Symlink creation is unavailable on Windows/MSYS; this sub-check runs
# where real symlinks exist, matching the other installer suites.)
rm -f "$fixture/cfg/region_props_applied" "$fixture/cfg/link_probe"
ln -s "$fixture/props/rt/persist.radio.skhwc_matchres" "$fixture/cfg/link_probe" 2>/dev/null || true
if [ ! -L "$fixture/cfg/link_probe" ]; then
  echo "SKIP: symlinks unavailable, skipping symlink refusal sub-check"
else
  rm -f "$fixture/cfg/link_probe"
  ln -s "$fixture/props/rt/persist.radio.skhwc_matchres" "$fixture/cfg/region_props_applied"
  run_case 'mark_region_props_applied'
  [ -L "$fixture/cfg/region_props_applied" ] || fail "symlink marker must be left untouched"
fi

echo "persist identity cleanup regression tests passed"
