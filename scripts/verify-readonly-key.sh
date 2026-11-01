#!/bin/bash
# Verifies that an Immich API key is read-only. Reads the server URL and the key
# interactively; the key is handed to curl through a config on stdin, so it never
# appears in process arguments, shell history or output. Response bodies are not printed.
# Compatible with macOS bash 3.2 and BSD tools.
set -u

printf 'Immich server URL (e.g. https://immich.example.com): '
read -r BASE_URL
printf 'API key (input hidden): '
read -r -s API_KEY
printf '\n'

BASE_URL="${BASE_URL%/}"
if [ -z "$BASE_URL" ] || [ -z "$API_KEY" ]; then
  echo "URL and key are required." >&2
  exit 2
fi
case "$BASE_URL" in
  https://*) ;;
  *) echo "Refusing non-HTTPS URL." >&2; exit 2 ;;
esac
case "$API_KEY" in
  *'"'*|*'\'*|*$'\n'*) echo "Key contains unsupported characters." >&2; exit 2 ;;
esac

FAILED=0

# request METHOD PATH BODY -> echoes the HTTP status code (000 on failure)
request() {
  local method="$1" path="$2" body="$3" code
  local -a args
  args=(--config - -sS -o /dev/null -w '%{http_code}' --max-time 30 -X "$method")
  if [ -n "$body" ]; then
    args+=(-H 'Content-Type: application/json' --data "$body")
  fi
  args+=("$BASE_URL$path")
  # The header (with the key) goes through stdin only.
  code=$(printf 'header = "x-api-key: %s"\n' "$API_KEY" | curl "${args[@]}" 2>/dev/null) || code="000"
  echo "$code"
}

# check LABEL EXPECTED(space separated codes) METHOD PATH [BODY]
check() {
  local label="$1" expected="$2" method="$3" path="$4" body="${5:-}" code ok=0 e
  code=$(request "$method" "$path" "$body")
  for e in $expected; do
    [ "$code" = "$e" ] && ok=1
  done
  if [ "$ok" = 1 ]; then
    echo "PASS  $label -> $code"
  else
    echo "FAIL  $label -> $code (expected: $expected)"
    FAILED=1
  fi
}

new_uuid() { uuidgen | tr '[:upper:]' '[:lower:]'; }

check "GET /api/albums"            200 GET  /api/albums
check "GET /api/people"            200 GET  /api/people
check "GET /api/timeline/buckets"  200 GET  /api/timeline/buckets
check "POST /api/search/metadata"  200 POST /api/search/metadata '{"size":1}'
check "POST /api/search/smart"     200 POST /api/search/smart '{"query":"playa","size":1}'
check "GET /api/admin/users (must be denied)" "401 403" GET /api/admin/users
check "PUT /api/assets/<random> (must be denied)" "401 403" PUT "/api/assets/$(new_uuid)" '{"isFavorite":true}'
check "DELETE /api/albums/<random> (must be denied)" "401 403" DELETE "/api/albums/$(new_uuid)"

unset API_KEY
if [ "$FAILED" = 0 ]; then
  echo "All checks passed: the key looks read-only."
  exit 0
fi
echo "Some checks FAILED: do not use this key."
exit 1
