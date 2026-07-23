#!/usr/bin/env bash
set -u

BASE_URL="${1:-}"
if [[ -z "$BASE_URL" ]]; then
  echo "Usage: $0 <base_url>" >&2
  exit 2
fi

command -v curl >/dev/null 2>&1 || {
  echo "curl est requis" >&2
  exit 2
}

BASE="${BASE_URL%/}"
INDEX_FILE="$(mktemp)"
HEALTH_FILE="$(mktemp)"
VIDEOS_FILE="$(mktemp)"
trap 'rm -f "$INDEX_FILE" "$HEALTH_FILE" "$VIDEOS_FILE"' EXIT

OK=0
FAIL=0

check() {
  local label="$1"
  local url="$2"
  local output="$3"
  local pattern="$4"

  if curl --fail --silent --show-error --max-time 15 "$url" --output "$output" \
      && grep -Eq "$pattern" "$output"; then
    echo "OK   $label"
    OK=$((OK + 1))
  else
    echo "FAIL $label" >&2
    FAIL=$((FAIL + 1))
  fi
}

check "SPA" "$BASE/" "$INDEX_FILE" '<ugo-root|<title>UGO2'
check "Santé API" "$BASE/api/v1/health" "$HEALTH_FILE" '"service"[[:space:]]*:[[:space:]]*"ugo2-api"'
check "Liste API" "$BASE/api/v1/videos?page=1&size=1" "$VIDEOS_FILE" '"items"[[:space:]]*:'

echo "$OK succès, $FAIL échec(s)"
[[ $FAIL -eq 0 ]]
