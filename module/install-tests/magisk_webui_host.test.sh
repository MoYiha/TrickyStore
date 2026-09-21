#!/usr/bin/env bash
# Magisk WebUI host launcher contract for module/template/action.sh.
#
# Exercises the launcher with stubbed Android commands and asserts:
# - module + webroot presence checks, unsafe id rejection
# - installed host detection via `pm path` (no re-download)
# - launch command carries `-e id cleverestricky` and the host activity
# - KernelSU/APatch runs the emergency report instead of downloading a host
# - missing host triggers exactly one GitHub API lookup + one APK download,
#   pinned-hash verification, install, and launch
# - unpinned release URLs, hash mismatches, malformed API responses, download
#   failures, and install failures fail closed to manual install with no
#   launch attempt and no leftover temp files
# - the Magisk busybox wget branch works when curl is absent
set -euo pipefail

REPO_ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
ACTION_SH="$REPO_ROOT/module/template/action.sh"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

new_fixture() {
  fixture=$(mktemp -d)
  mkdir -p "$fixture/mod/webroot" "$fixture/stubbin" "$fixture/tmp"
  printf '<html></html>' > "$fixture/mod/webroot/index.html"
  printf '' > "$fixture/am.log"
  printf '' > "$fixture/curl.log"
  printf '' > "$fixture/pm.log"
  printf '' > "$fixture/busybox.log"
}

make_toolbin() {
  fixture=$1
  mkdir -p "$fixture/toolbin"
  for tool in mktemp chmod rm grep cut head wc od tr sha256sum mkdir cat touch timeout; do
    tool_path=$(command -v "$tool" 2>/dev/null) || continue
    case "$tool_path" in "$fixture"/stubbin*|"$fixture"/toolbin*) continue ;; esac
    ln -s "$tool_path" "$fixture/toolbin/$tool" 2>/dev/null || cp "$tool_path" "$fixture/toolbin/$tool" 2>/dev/null || true
  done
  # Downloader tools must never leak into the isolated PATH: curl, wget, and
  # any busybox would bypass the branch under test.
  rm -f "$fixture/toolbin/curl" "$fixture/toolbin/wget" "$fixture/toolbin/busybox"
}

write_stubs() {
  fixture=$1
  cat > "$fixture/stubbin/pm" <<'STUB'
#!/bin/bash
echo "pm $*" >> "$FIXTURE/pm.log"
if [[ "${1:-}" == "path" ]]; then
  [[ -f "$FIXTURE/installed" ]] && exit 0
  exit "${PM_PATH_RESULT:-1}"
fi
if [[ "${1:-}" == "install" ]]; then
  [[ "${PM_INSTALL_RESULT:-0}" == "0" ]] && { touch "$FIXTURE/installed"; exit 0; }
  exit 1
fi
exit 0
STUB
  cat > "$fixture/stubbin/am" <<'STUB'
#!/bin/bash
echo "am $*" >> "$FIXTURE/am.log"
exit "${AM_RESULT:-0}"
STUB
  cat > "$fixture/stubbin/curl" <<'STUB'
#!/bin/bash
echo "curl $*" >> "$FIXTURE/curl.log"
url="${@: -1}"
out=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  if [[ "${args[$i]}" == "-o" && $((i + 1)) -lt ${#args[@]} ]]; then
    out="${args[$((i + 1))]}"
  fi
done
if [[ "$url" == *"api.github.com"* ]]; then
  [[ "${CURL_API_RESULT:-0}" == "0" ]] || exit 1
  body=$(cat "$FIXTURE/api.json")
else
  [[ "${CURL_APK_RESULT:-0}" == "0" ]] || exit 1
  body=$(cat "$FIXTURE/apk.bin")
fi
if [[ -n "$out" ]]; then
  printf '%s' "$body" > "$out"
else
  printf '%s' "$body"
fi
exit 0
STUB
  cat > "$fixture/stubbin/busybox" <<'STUB'
#!/bin/bash
echo "busybox $*" >> "$FIXTURE/busybox.log"
if [[ "${1:-}" == "wget" ]]; then
  [[ "${BUSYBOX_WGET_RESULT:-0}" == "0" ]] || exit 1
  args=("$@")
  url="${args[${#args[@]}-1]}"
  out=""
  for ((i = 0; i < ${#args[@]}; i++)); do
    if [[ "${args[$i]}" == "-O" && $((i + 1)) -lt ${#args[@]} ]]; then
      out="${args[$((i + 1))]}"
    fi
  done
  if [[ "$url" == *"api.github.com"* ]]; then
    body=$(cat "$FIXTURE/api.json")
  else
    body=$(cat "$FIXTURE/apk.bin")
  fi
  stdout_mode=0
  for arg in "$@"; do
    [[ "$arg" == "-qO-" || "$arg" == "-" ]] && stdout_mode=1
  done
  if [[ $stdout_mode -eq 1 ]]; then
    printf '%s' "$body"
  elif [[ -n "$out" ]]; then
    printf '%s' "$body" > "$out"
  else
    exit 1
  fi
  exit 0
fi
if [[ "${1:-}" == "sha256sum" ]]; then
  sha256sum "${@:2}"
  exit $?
fi
exit 1
STUB
  chmod +x "$fixture/stubbin/pm" "$fixture/stubbin/am" "$fixture/stubbin/curl" "$fixture/stubbin/busybox"
}

write_api() {
  fixture=$1
  url=$2
  printf '{"assets": [{"browser_download_url": "%s"}]}' "$url" > "$fixture/api.json"
}

write_apk() {
  fixture=$1
  { printf 'PK'; head -c 200000 /dev/zero | tr '\0' 'A'; } > "$fixture/apk.bin"
}

write_pin() {
  fixture=$1
  url=$2
  sha=$3
  printf 'v9.9 %s %s\n' "$sha" "$url" > "$fixture/mod/webui-host.sha256"
}

pin_for_apk() {
  fixture=$1
  url=$2
  sha256sum "$fixture/apk.bin" | cut -d ' ' -f 1
}

run_launcher() {
  fixture=$1
  (
    export FIXTURE="$fixture"
    export PATH="$fixture/stubbin:$PATH"
    export MODULE_ID=cleverestricky
    export MODDIR="$fixture/mod"
    export WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX"
    export MAGISK_BUSYBOX="$fixture/stubbin/busybox"
    bash "$ACTION_SH"
  )
}

assert_am_launch() {
  fixture=$1
  grep -Fq "am start -n io.github.a13e300.ksuwebui/.WebUIActivity -e id cleverestricky" "$fixture/am.log" \
    || fail "launcher did not start the WebUI host with id=cleverestricky (got: $(cat "$fixture/am.log"))"
}

PIN_URL="https://objects.githubusercontent.com/host.apk"

# Scenario 1: host already installed -> launch only, no download.
new_fixture
write_stubs "$fixture"
touch "$fixture/installed"
set +e
run_launcher "$fixture" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -eq 0 ]] || fail "installed-host launch exited $status: $(cat "$fixture/out.log")"
assert_am_launch "$fixture"
[[ ! -s "$fixture/curl.log" ]] || fail "installed host must not trigger a download"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "installed host left temp files behind"
rm -rf "$fixture"

# Scenario 2: host missing + pinned release -> API lookup + download + hash + install + launch once.
# The API lists a debug asset first; the launcher must pick the exact pinned asset.
new_fixture
write_stubs "$fixture"
printf '{"assets": [{"browser_download_url": "https://objects.githubusercontent.com/debug.apk"}, {"browser_download_url": "%s"}]}' "$PIN_URL" > "$fixture/api.json"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "$(pin_for_apk "$fixture" "$PIN_URL")"
set +e
run_launcher "$fixture" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -eq 0 ]] || fail "fresh install flow exited $status: $(cat "$fixture/out.log")"
assert_am_launch "$fixture"
[[ $(grep -c "api.github.com/repos/adivenxnataly/KsuWebUI/releases/latest" "$fixture/curl.log") -eq 1 ]] \
  || fail "expected exactly one release API lookup"
[[ $(grep -c "objects.githubusercontent.com/host.apk" "$fixture/curl.log") -eq 1 ]] \
  || fail "expected exactly one APK download"
[[ $(grep -c "debug.apk" "$fixture/curl.log" || true) -eq 0 ]] \
  || fail "launcher must download the pinned asset, not the first listed APK"
grep -Fq "pm install -r" "$fixture/pm.log" || fail "expected pm install -r"
[[ -f "$fixture/installed" ]] || fail "install was not recorded"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "fresh install left temp files behind"
rm -rf "$fixture"

# Scenario 2b: same flow through the Magisk busybox wget branch (no curl).
new_fixture
write_stubs "$fixture"
write_api "$fixture" "$PIN_URL"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "$(pin_for_apk "$fixture" "$PIN_URL")"
mkdir -p "$fixture/stubbin-nocurl"
for tool in pm am busybox; do cp "$fixture/stubbin/$tool" "$fixture/stubbin-nocurl/$tool"; done
chmod +x "$fixture/stubbin-nocurl/"*
make_toolbin "$fixture"
[[ ! -e "$fixture/toolbin/curl" && ! -e "$fixture/toolbin/wget" && ! -e "$fixture/toolbin/busybox" ]] \
  || fail "isolated toolbin leaked a downloader"
# MSYS symlinks resolve their runtime libraries through PATH. If a system
# directory carries the core tools but no downloader, extend the isolated
# PATH with it (dev-machine accommodation). On CI runners /usr/bin holds
# curl, so this never triggers there and isolation stays strict.
isolated_path="$fixture/stubbin-nocurl:$fixture/toolbin"
if [ ! -e /usr/bin/curl ] && [ ! -e /usr/bin/wget ] && [ -d /usr/bin ]; then
  isolated_path="$isolated_path:/usr/bin"
fi
set +e
(
  export FIXTURE="$fixture"
  export PATH="$isolated_path"
  export MODULE_ID=cleverestricky
  export MODDIR="$fixture/mod"
  export WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX"
  export MAGISK_BUSYBOX="$fixture/stubbin-nocurl/busybox"
  "$BASH" "$ACTION_SH" > "$fixture/out.log" 2>&1
)
status=$?
set -e
[[ $status -eq 0 ]] || fail "busybox-branch install exited $status: $(cat "$fixture/out.log")"
assert_am_launch "$fixture"
grep -Fq "busybox wget" "$fixture/busybox.log" || fail "expected the Magisk busybox wget branch"
[[ -f "$fixture/installed" ]] || fail "busybox-branch install was not recorded"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "busybox branch left temp files behind"
rm -rf "$fixture"

# Scenario 3: malformed API response (no apk asset) -> fail closed, no install, no launch.
new_fixture
write_stubs "$fixture"
printf '{"assets": []}' > "$fixture/api.json"
write_pin "$fixture" "$PIN_URL" "0000000000000000000000000000000000000000000000000000000000000000"
set +e
run_launcher "$fixture" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "malformed API response unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "malformed API must not launch WebUI"
grep -Fq "pm install" "$fixture/pm.log" && fail "malformed API must not install anything"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "malformed API left temp files behind"
rm -rf "$fixture"

# Scenario 4: APK download failure -> fail closed, no install, no launch.
new_fixture
write_stubs "$fixture"
write_api "$fixture" "$PIN_URL"
write_pin "$fixture" "$PIN_URL" "0000000000000000000000000000000000000000000000000000000000000000"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin:$PATH" MODULE_ID=cleverestricky MODDIR="$fixture/mod" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" MAGISK_BUSYBOX="$fixture/stubbin/busybox" \
  CURL_APK_RESULT=1 bash "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "download failure unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "download failure must not launch WebUI"
grep -Fq "pm install" "$fixture/pm.log" && fail "download failure must not install anything"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "download failure left temp files behind"
rm -rf "$fixture"

# Scenario 5: pm install failure -> fail closed, no launch.
new_fixture
write_stubs "$fixture"
write_api "$fixture" "$PIN_URL"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "$(pin_for_apk "$fixture" "$PIN_URL")"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin:$PATH" MODULE_ID=cleverestricky MODDIR="$fixture/mod" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" MAGISK_BUSYBOX="$fixture/stubbin/busybox" \
  PM_INSTALL_RESULT=1 bash "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "install failure unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "install failure must not launch WebUI"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "install failure left temp files behind"
rm -rf "$fixture"

# Scenario 5b: hash mismatch -> fail closed to manual install, no install, no launch.
new_fixture
write_stubs "$fixture"
write_api "$fixture" "$PIN_URL"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
set +e
run_launcher "$fixture" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "hash mismatch unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "hash mismatch must not launch WebUI"
grep -Fq "pm install" "$fixture/pm.log" && fail "hash mismatch must not install anything"
grep -Fq "manually" "$fixture/out.log" || fail "hash mismatch must point at manual install"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "hash mismatch left temp files behind"
rm -rf "$fixture"

# Scenario 5c: rotated upstream release (URL not pinned) -> manual install, no download of bytes.
new_fixture
write_stubs "$fixture"
write_api "$fixture" "https://objects.githubusercontent.com/new-release.apk"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "$(pin_for_apk "$fixture" "$PIN_URL")"
set +e
run_launcher "$fixture" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "unpinned release unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "unpinned release must not launch WebUI"
grep -Fq "pm install" "$fixture/pm.log" && fail "unpinned release must not install anything"
[[ $(grep -c "objects.githubusercontent.com" "$fixture/curl.log" || true) -eq 0 ]] \
  || fail "unpinned release must stop after the API lookup without downloading bytes"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "unpinned release left temp files behind"
rm -rf "$fixture"

# Scenario 5d: oversized APK body fails closed before hashing.
new_fixture
write_stubs "$fixture"
write_api "$fixture" "$PIN_URL"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "$(pin_for_apk "$fixture" "$PIN_URL")"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin:$PATH" MODULE_ID=cleverestricky MODDIR="$fixture/mod" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" MAGISK_BUSYBOX="$fixture/stubbin/busybox" \
  WEBUI_HOST_MAX_APK_BYTES=1000 bash "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "oversized APK unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "oversized APK must not launch WebUI"
grep -Fq "pm install" "$fixture/pm.log" && fail "oversized APK must not install anything"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "oversized APK left temp files behind"
rm -rf "$fixture"

# Scenario 5e: stock wget without timeout is rejected, never attempted.
new_fixture
write_stubs "$fixture"
write_api "$fixture" "$PIN_URL"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "$(pin_for_apk "$fixture" "$PIN_URL")"
mkdir -p "$fixture/stubbin-wgetonly"
for tool in pm am; do cp "$fixture/stubbin/$tool" "$fixture/stubbin-wgetonly/$tool"; done
cat > "$fixture/stubbin-wgetonly/wget" <<'STUB'
#!/bin/bash
echo "wget $*" >> "$FIXTURE/wget.log"
exit 1
STUB
chmod +x "$fixture/stubbin-wgetonly/"*
make_toolbin "$fixture"
rm -f "$fixture/toolbin/timeout" "$fixture/toolbin/curl" "$fixture/toolbin/wget"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin-wgetonly:$fixture/toolbin" MODULE_ID=cleverestricky MODDIR="$fixture/mod" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" MAGISK_BUSYBOX="$fixture/absent-busybox" \
  "$BASH" "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "timeout-less wget unexpectedly succeeded"
grep -Fq "No downloader available" "$fixture/out.log" || fail "must report the missing downloader"
[[ ! -f "$fixture/wget.log" ]] || fail "rejected wget branch must never run"
[[ ! -s "$fixture/am.log" ]] || fail "rejected downloader must not launch WebUI"
rm -rf "$fixture"

# Scenario 6: missing module directory -> fail closed before touching pm/curl.
new_fixture
rm -rf "$fixture/mod"
mkdir -p "$fixture/stubbin" "$fixture/tmp"
write_stubs "$fixture"
write_api "$fixture" "$PIN_URL"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin:$PATH" MODULE_ID=cleverestricky MODDIR="$fixture/absent" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" bash "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "missing module directory unexpectedly succeeded"
[[ ! -s "$fixture/am.log" && ! -s "$fixture/curl.log" ]] || fail "missing module must not reach host/download paths"
rm -rf "$fixture"

# Scenario 6b: KernelSU/APatch manager WebUI available -> runs the emergency report, no host download.
new_fixture
write_stubs "$fixture"
cat > "$fixture/mod/emergency-report.sh" <<'STUB'
#!/bin/bash
echo "emergency-report $*" >> "$FIXTURE/report.log"
exit 0
STUB
chmod +x "$fixture/mod/emergency-report.sh"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin:$PATH" MODULE_ID=cleverestricky MODDIR="$fixture/mod" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" KSU=true bash "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -eq 0 ]] || fail "manager-WebUI path exited $status: $(cat "$fixture/out.log")"
grep -Fq "emergency-report" "$fixture/report.log" || fail "manager WebUI must run the emergency report"
[[ ! -s "$fixture/curl.log" ]] || fail "manager WebUI must not trigger a download"
grep -Fq "pm install" "$fixture/pm.log" && fail "manager WebUI must not install anything"
[[ ! -s "$fixture/am.log" ]] || fail "manager WebUI must not launch the standalone host"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "manager WebUI left temp files behind"
rm -rf "$fixture"

# Scenario 6c: manager WebUI wins even when the host is installed.
new_fixture
write_stubs "$fixture"
touch "$fixture/installed"
cat > "$fixture/mod/emergency-report.sh" <<'STUB'
#!/bin/bash
echo "emergency-report $*" >> "$FIXTURE/report.log"
exit 0
STUB
chmod +x "$fixture/mod/emergency-report.sh"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin:$PATH" MODULE_ID=cleverestricky MODDIR="$fixture/mod" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" KSU=true bash "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -eq 0 ]] || fail "manager-first path exited $status: $(cat "$fixture/out.log")"
grep -Fq "emergency-report" "$fixture/report.log" || fail "installed host must not divert the manager flow"
[[ ! -s "$fixture/am.log" ]] || fail "manager flow must not launch the standalone host"
[[ ! -s "$fixture/curl.log" ]] || fail "manager flow must not touch the network"
rm -rf "$fixture"

# Scenario 7: unsafe module id is rejected.
new_fixture
write_stubs "$fixture"
touch "$fixture/installed"
set +e
FIXTURE="$fixture" PATH="$fixture/stubbin:$PATH" MODULE_ID='evil;id' MODDIR="$fixture/mod" \
  WEBUI_HOST_TMP_TEMPLATE="$fixture/tmp/webui.XXXXXX" bash "$ACTION_SH" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "unsafe module id unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "unsafe module id must not launch WebUI"
rm -rf "$fixture"

# Scenario 7b: off-allowlist APK host fails closed (transport endpoint pinning).
new_fixture
write_stubs "$fixture"
write_api "$fixture" "https://evil.example.com/host.apk"
write_apk "$fixture"
write_pin "$fixture" "$PIN_URL" "$(pin_for_apk "$fixture" "$PIN_URL")"
set +e
run_launcher "$fixture" > "$fixture/out.log" 2>&1
status=$?
set -e
[[ $status -ne 0 ]] || fail "off-allowlist APK host unexpectedly succeeded"
[[ ! -s "$fixture/am.log" ]] || fail "off-allowlist APK host must not launch WebUI"
grep -Fq "pm install" "$fixture/pm.log" && fail "off-allowlist APK host must not install anything"
[[ -z "$(ls -A "$fixture/tmp")" ]] || fail "off-allowlist APK host left temp files behind"
rm -rf "$fixture"

echo "Magisk WebUI host launcher tests passed"
