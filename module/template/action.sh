#!/system/bin/sh

PORT_FILE="/data/adb/cleverestricky/web_port"
HOST="127.0.0.1"
MAX_WAIT_SECONDS=15

if [ ! -f "$PORT_FILE" ] || [ -L "$PORT_FILE" ]; then
  echo "! Web server port file not found or unsafe. Is the module running?"
  exit 1
fi

PORT_FILE_SIZE=$(wc -c < "$PORT_FILE" 2>/dev/null) || {
  echo "! Failed to read WebUI endpoint metadata."
  exit 1
}
if [ "$PORT_FILE_SIZE" -lt 1 ] || [ "$PORT_FILE_SIZE" -gt 256 ]; then
  echo "! Invalid WebUI endpoint metadata size."
  exit 1
fi

IFS= read -r CONTENT < "$PORT_FILE"
case "$CONTENT" in
  *'|'*) ;;
  *)
    echo "! Invalid port file content."
    exit 1
    ;;
esac
PORT=${CONTENT%%|*}
TOKEN=${CONTENT#*|}

if [ -z "$PORT" ] || [ -z "$TOKEN" ]; then
  echo "! Invalid port file content."
  exit 1
fi

case "$PORT" in
  ''|*[!0-9]*)
    echo "! Invalid WebUI port: $PORT"
    exit 1
    ;;
esac

if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
  echo "! WebUI port out of range: $PORT"
  exit 1
fi

case "$TOKEN" in
  *[!A-Za-z0-9_-]*)
    echo "! Invalid WebUI token."
    exit 1
    ;;
esac
if [ "${#TOKEN}" -lt 32 ] || [ "${#TOKEN}" -gt 128 ]; then
  echo "! Invalid WebUI token length."
  exit 1
fi

URL="http://$HOST:$PORT/?token=$TOKEN"

echo "- Waiting for WebUI to listen on $HOST:$PORT"
READY=0
ATTEMPT=0
while [ "$ATTEMPT" -lt "$MAX_WAIT_SECONDS" ]; do
  if [ -f "/proc/net/tcp" ] && grep -q -i ":$(printf '%04X' "$PORT") " /proc/net/tcp; then
    READY=1
    break
  elif [ -f "/proc/net/tcp6" ] && grep -q -i ":$(printf '%04X' "$PORT") " /proc/net/tcp6; then
    READY=1
    break
  elif command -v nc >/dev/null 2>&1 && nc -z -w 1 "$HOST" "$PORT" >/dev/null 2>&1; then
    READY=1
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  sleep 1
done

if [ "$READY" -ne 1 ]; then
  echo "! WebUI did not report ready within ${MAX_WAIT_SECONDS}s; launching browser anyway for debugging"
  log -t CleveresTricky "WebUI readiness probe timed out on $HOST:$PORT"
fi

echo "- Opening WebUI"
START_OUTPUT=$(am start -W -f 0x10000000 -a android.intent.action.VIEW -d "$URL" 2>&1)
START_EXIT=$?

case "$START_OUTPUT" in
  *ActivityNotFoundException*|*unable\ to\ resolve\ Intent*)
    BROWSER_ERROR=1
    ;;
  *)
    BROWSER_ERROR=0
    ;;
esac

if [ "$START_EXIT" -ne 0 ]; then
  echo "! Failed to launch WebUI intent (exit $START_EXIT)"
  log -t CleveresTricky "WebUI launch failed with exit $START_EXIT"
  if [ "$BROWSER_ERROR" -eq 1 ]; then
    echo "! No browser is installed to handle the WebUI link."
  fi
  exit "$START_EXIT"
fi

if [ "$BROWSER_ERROR" -eq 1 ]; then
  echo "! No browser is installed to handle the WebUI link."
  log -t CleveresTricky "WebUI launch failed: no browser handler"
  exit 1
fi

log -t CleveresTricky "WebUI launch intent started"
