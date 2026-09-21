#!/system/bin/sh

set -u

MODULE_ID="${MODULE_ID:-cleverestricky}"
MODDIR="${MODDIR:-/data/adb/modules/$MODULE_ID}"
WEBUI_HOST_PKG="${WEBUI_HOST_PKG:-io.github.a13e300.ksuwebui}"
WEBUI_HOST_ACTIVITY="${WEBUI_HOST_ACTIVITY:-io.github.a13e300.ksuwebui/.WebUIActivity}"
WEBUI_HOST_REPO="${WEBUI_HOST_REPO:-adivenxnataly/KsuWebUI}"
WEBUI_HOST_RELEASES_URL="https://github.com/$WEBUI_HOST_REPO/releases"
WEBUI_HOST_TMP_TEMPLATE="${WEBUI_HOST_TMP_TEMPLATE:-/data/local/tmp/cleverestricky-webui.XXXXXX}"
MAGISK_BUSYBOX="${MAGISK_BUSYBOX:-/data/adb/magisk/busybox}"
HOST_PIN_FILE="$MODDIR/webui-host.sha256"

TMP_DIR=""
APK_PATH=""

cleanup() {
    if [ -n "$APK_PATH" ] && [ ! -L "$APK_PATH" ]; then
        rm -f "$APK_PATH" 2>/dev/null || true
    fi
    if [ -n "$TMP_DIR" ] && [ -d "$TMP_DIR" ] && [ ! -L "$TMP_DIR" ]; then
        rm -rf "$TMP_DIR" 2>/dev/null || true
    fi
    APK_PATH=""
    TMP_DIR=""
}
trap cleanup EXIT
trap 'exit 1' INT TERM

fail_manual() {
    printf '%s\n' "! $1"
    printf '%s\n' "- Download the WebUI host APK manually from:"
    printf '%s\n' "  $WEBUI_HOST_RELEASES_URL"
    printf '%s\n' "- Install it, then press Action again."
    exit 1
}

host_installed() {
    pm path "$WEBUI_HOST_PKG" >/dev/null 2>&1
}

manager_webui_available() {
    [ "${KSU:-false}" = "true" ] && return 0
    [ "${APATCH:-false}" = "true" ] && return 0
    [ -d /data/adb/ksu ] && return 0
    [ -d /data/adb/ap ] && return 0
    return 1
}

launch_webui() {
    if [ -z "$MODULE_ID" ]; then
        printf '%s\n' "! Refusing to launch WebUI with an empty module id."
        return 1
    fi
    case "$MODULE_ID" in
        *[!A-Za-z0-9_.-]*) printf '%s\n' "! Refusing to launch WebUI with an unsafe module id."; return 1 ;;
    esac
    if ! am start -n "$WEBUI_HOST_ACTIVITY" -e id "$MODULE_ID" >/dev/null 2>&1; then
        printf '%s\n' "! Could not start the WebUI host activity ($WEBUI_HOST_ACTIVITY)."
        return 1
    fi
}

WEBUI_HOST_MAX_API_BYTES=65536
WEBUI_HOST_MAX_APK_BYTES="${WEBUI_HOST_MAX_APK_BYTES:-33554432}"
probe_downloader() {
    if command -v curl >/dev/null 2>&1; then
        DOWNLOADER=curl
        return 0
    fi
    if [ -n "$MAGISK_BUSYBOX" ] && [ -f "$MAGISK_BUSYBOX" ] && [ ! -L "$MAGISK_BUSYBOX" ] && [ -x "$MAGISK_BUSYBOX" ]; then
        DOWNLOADER=magisk-wget
        return 0
    fi
    if command -v wget >/dev/null 2>&1 && command -v timeout >/dev/null 2>&1; then
        DOWNLOADER=wget
        return 0
    fi
    DOWNLOADER=none
    return 1
}

require_https_url() {
    case "${1:-}" in
        https://*) return 0 ;;
        *) return 1 ;;
    esac
}

fetch_stdout() {
    require_https_url "$1" || return 1
    case "$DOWNLOADER" in
        curl) curl --connect-timeout 10 --max-time 180 --max-filesize "$WEBUI_HOST_MAX_API_BYTES" -fsSL "$1" ;;
        magisk-wget) "$MAGISK_BUSYBOX" wget -T 15 -qO- "$1" | head -c "$WEBUI_HOST_MAX_API_BYTES" ;;
        wget) timeout 180 wget -O- "$1" 2>/dev/null | head -c "$WEBUI_HOST_MAX_API_BYTES" ;;
        *) return 127 ;;
    esac
}

fetch_file() {
    require_https_url "$1" || return 1
    case "$DOWNLOADER" in
        curl) curl --connect-timeout 10 --max-time 180 --max-filesize "$WEBUI_HOST_MAX_APK_BYTES" -fsSL -o "$2" "$1" ;;
        magisk-wget) "$MAGISK_BUSYBOX" wget -T 15 -q -O "$2" "$1" ;;
        wget) timeout 180 wget -O "$2" "$1" >/dev/null 2>&1 ;;
        *) return 127 ;;
    esac
}

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" 2>/dev/null | cut -d ' ' -f 1
    elif [ -n "$MAGISK_BUSYBOX" ] && [ -f "$MAGISK_BUSYBOX" ] && [ ! -L "$MAGISK_BUSYBOX" ] && [ -x "$MAGISK_BUSYBOX" ]; then
        "$MAGISK_BUSYBOX" sha256sum "$1" 2>/dev/null | cut -d ' ' -f 1
    else
        return 1
    fi
}

is_allowed_apk_url() {
    case "$1" in
        https://github.com/*|https://objects.githubusercontent.com/*|https://*.githubusercontent.com/*) return 0 ;;
        *) return 1 ;;
    esac
}

latest_apk_url() {
    case "$WEBUI_HOST_REPO" in
        */*) repo_owner=${WEBUI_HOST_REPO%%/*}; repo_name=${WEBUI_HOST_REPO#*/} ;;
        *) return 1 ;;
    esac
    case "$repo_owner" in ""|*[!A-Za-z0-9_.-]*) return 1 ;; esac
    case "$repo_name" in ""|*[!A-Za-z0-9_.-]*|*/*) return 1 ;; esac
    api_response=$(fetch_stdout "https://api.github.com/repos/$WEBUI_HOST_REPO/releases/latest") || return 1
    [ -n "$api_response" ] || return 1
    apk_urls=$(printf '%s' "$api_response" | grep -o '"browser_download_url": "[^"]*\.apk"' | cut -d '"' -f 4)
    [ -n "$apk_urls" ] || return 1
    apk_url=$(printf '%s\n' "$apk_urls" | head -n 1)
    if [ -n "${PIN_URL:-}" ]; then
        pinned_hit=$(printf '%s\n' "$apk_urls" | grep -Fx "$PIN_URL" | head -n 1)
        [ -n "$pinned_hit" ] && apk_url=$pinned_hit
    fi
    [ -n "$apk_url" ] || return 1
    is_allowed_apk_url "$apk_url" || return 1
    printf '%s' "$apk_url"
}

read_host_pin() {
    pin_path=$1
    [ -f "$pin_path" ] && [ ! -L "$pin_path" ] || return 1
    pin_size=$(wc -c < "$pin_path" 2>/dev/null | tr -d '[:space:]') || return 1
    case "$pin_size" in ''|*[!0-9]*) return 1 ;; esac
    [ "$pin_size" -ge 10 ] && [ "$pin_size" -le 4096 ] || return 1
    pin_line=$(grep -v '^#' "$pin_path" 2>/dev/null | grep -v '^[[:space:]]*$' | head -n 1) || return 1
    [ -n "$pin_line" ] || return 1
    PIN_VERSION=$(printf '%s' "$pin_line" | cut -d ' ' -f 1)
    PIN_SHA=$(printf '%s' "$pin_line" | cut -d ' ' -f 2)
    PIN_URL=$(printf '%s' "$pin_line" | cut -d ' ' -f 3)
    case "$PIN_VERSION" in ""|*[!A-Za-z0-9_.-]*) return 1 ;; esac
    [ "${#PIN_SHA}" -eq 64 ] || return 1
    case "$PIN_SHA" in *[!0-9a-f]*) return 1 ;; esac
    is_allowed_apk_url "$PIN_URL" || return 1
    return 0
}

apk_looks_valid() {
    candidate=$1
    [ -f "$candidate" ] && [ ! -L "$candidate" ] || return 1
    candidate_size=$(wc -c < "$candidate" 2>/dev/null | tr -d '[:space:]') || return 1
    case "$candidate_size" in ''|*[!0-9]*) return 1 ;; esac
    [ "$candidate_size" -gt 102400 ] || return 1
    [ "$candidate_size" -le "$WEBUI_HOST_MAX_APK_BYTES" ] || return 1
    if command -v od >/dev/null 2>&1 && command -v head >/dev/null 2>&1; then
        magic=$(head -c 2 "$candidate" 2>/dev/null | od -An -tx1 2>/dev/null | tr -d ' \n')
        [ "$magic" = "504b" ] || return 1
    fi
    return 0
}

install_webui_host() {
    printf '%s\n' "- WebUI host ($WEBUI_HOST_PKG) is not installed."
    probe_downloader || fail_manual "No downloader available (need curl, Magisk busybox, or wget)."
    read_host_pin "$HOST_PIN_FILE" || fail_manual "Reviewed host release pin is missing or invalid."
    apk_url=$(latest_apk_url) || fail_manual "Could not resolve the latest WebUI host release."
    if [ "$apk_url" != "$PIN_URL" ]; then
        fail_manual "A new host release ($apk_url) has no reviewed pin yet."
    fi
    printf '%s\n' "- Downloading reviewed WebUI host $PIN_VERSION (standalone container, not a root manager)..."
    TMP_DIR=$(mktemp -d "$WEBUI_HOST_TMP_TEMPLATE" 2>/dev/null) || fail_manual "Could not create a temporary download directory."
    chmod 700 "$TMP_DIR" 2>/dev/null || fail_manual "Could not secure the temporary download directory."
    APK_PATH="$TMP_DIR/webui-host.apk"
    if [ -e "$APK_PATH" ] || [ -L "$APK_PATH" ]; then
        fail_manual "Temporary download target is unsafe."
    fi
    : > "$APK_PATH" 2>/dev/null || fail_manual "Could not create the temporary download file."
    fetch_file "$apk_url" "$APK_PATH" || fail_manual "WebUI host APK download failed."
    apk_looks_valid "$APK_PATH" || fail_manual "Downloaded WebUI host APK failed validation (empty, truncated, or not an APK)."
    actual_sha=$(sha256_of "$APK_PATH") || fail_manual "Could not hash the downloaded WebUI host APK."
    if [ "$actual_sha" != "$PIN_SHA" ]; then
        fail_manual "Downloaded WebUI host APK does not match the reviewed pin."
    fi
    printf '%s\n' "- Installing WebUI host..."
    if ! pm install -r "$APK_PATH" >/dev/null 2>&1; then
        fail_manual "WebUI host APK installation failed."
    fi
    if ! host_installed; then
        fail_manual "WebUI host installation could not be verified."
    fi
    printf '%s\n' "- WebUI host installed."
}

if [ ! -d "$MODDIR" ] || [ -L "$MODDIR" ]; then
    printf '%s\n' "! CleveresTricky module directory is missing: $MODDIR"
    exit 1
fi
if [ -e "$MODDIR/remove" ]; then
    printf '%s\n' "! CleveresTricky is pending removal; WebUI is unavailable."
    exit 1
fi
if [ ! -f "$MODDIR/webroot/index.html" ] || [ -L "$MODDIR/webroot/index.html" ]; then
    printf '%s\n' "! CleveresTricky WebUI files are missing: $MODDIR/webroot/index.html"
    exit 1
fi

if manager_webui_available; then
    report_script="$MODDIR/emergency-report.sh"
    if [ -f "$report_script" ] && [ ! -L "$report_script" ] && [ -x "$report_script" ]; then
        exec "$report_script" ${1+"$@"}
    fi
    printf '%s\n' "! Emergency report script is missing: $report_script"
    exit 1
elif host_installed; then
    printf '%s\n' "- Launching CleveresTricky WebUI..."
    launch_webui || exit 1
else
    install_webui_host
    printf '%s\n' "- Launching CleveresTricky WebUI..."
    launch_webui || exit 1
fi

printf '%s\n' "- WebUI launched successfully."
