#!/usr/bin/env bash

# testui.sh v3.1 — UGO2 checks avec variantes anti-WAF
# Usage: ./testui.sh https://ugo2.capstv.ch [--verbose] [--insecure] [--no-color] [--only=ping,index,health,filter]

BASE_URL="${1:-}"
shift || true

VERBOSE=0; INSECURE=0; NOCOLOR=0; ONLY=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --verbose) VERBOSE=1 ;;
    --insecure) INSECURE=1 ;;
    --no-color) NOCOLOR=1 ;;
    --only=*) ONLY="${1#--only=}" ;;
    *) echo "Arg inconnu: $1" >&2 ;;
  esac
  shift
done
[[ -z "$BASE_URL" ]] && { echo "Usage: $0 <base_url> [...]"; exit 2; }

# couleurs
if [[ $NOCOLOR -eq 1 ]]; then GREEN=""; RED=""; YELLOW=""; CYAN=""; BOLD=""; RESET=""
else GREEN=$'\e[32m'; RED=$'\e[31m'; YELLOW=$'\e[33m'; CYAN=$'\e[36m'; BOLD=$'\e[1m'; RESET=$'\e[0m'; fi
pass(){ echo "${GREEN}✔${RESET} $*"; ((OK++)); }
fail(){ echo "${RED}✘${RESET} $*";  ((FAIL++)); }
skip(){ echo "${YELLOW}↷${RESET} $*"; ((SKIP++)); }
title(){ echo; echo "${BOLD}${CYAN}== $* ==${RESET}"; }
has_cmd(){ command -v "$1" >/dev/null 2>&1; }

OK=0; FAIL=0; SKIP=0
BASE="${BASE_URL%/}"

# curl opts
CURL_OPTS=( -m 15 --retry 1 --show-error )
[[ $VERBOSE -eq 1 ]] && CURL_OPTS+=( -v )
[[ $INSECURE -eq 1 ]] && CURL_OPTS+=( -k )

BROWSER_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119 Safari/537.36"

curl_capture() {
  local method="$1" url="$2" data="${3:-}" hdrs=("${@:4}")
  local -n _body=BODY _code=CODE _hdr=HDR
  local tmp_body tmp_code tmp_hdr; tmp_body="$(mktemp)"; tmp_code="$(mktemp)"; tmp_hdr="$(mktemp)"
  if [[ "$method" == "GET" ]]; then
    curl "${CURL_OPTS[@]}" -H 'Accept: application/json,*/*;q=0.8' \
      -H "User-Agent: ${BROWSER_UA}" -H "Referer: ${BASE}/" \
      "${hdrs[@]}" -D "$tmp_hdr" "$url" --write-out '%{http_code}' --output "$tmp_body" > "$tmp_code" 2>&1
  else
    curl "${CURL_OPTS[@]}" -H 'Accept: application/json,*/*;q=0.8' -H 'Content-Type: application/json' \
      -H "User-Agent: ${BROWSER_UA}" -H "Referer: ${BASE}/" \
      "${hdrs[@]}" -X "$method" -d "$data" -D "$tmp_hdr" "$url" --write-out '%{http_code}' --output "$tmp_body" > "$tmp_code" 2>&1
  fi
  CODE="$(tail -n1 "$tmp_code" | tr -dc '0-9')"
  BODY="$(cat "$tmp_body")"
  HDR="$(cat "$tmp_hdr")"
  rm -f "$tmp_body" "$tmp_code" "$tmp_hdr"
}

echo "${BOLD}UGO2 quick check${RESET} → ${BASE}"
[[ $INSECURE -eq 1 ]] && echo "(TLS: --insecure)"
[[ $VERBOSE -eq 1 ]] && echo "(curl verbose ON)"

# 1) ping
title "Test /ping.txt (doit répondre 'pong')"
curl_capture GET "${BASE}/ping.txt"
if echo "$BODY" | tr -d '\r' | grep -qi '^pong$'; then pass "/ping.txt → pong (HTTP $CODE)"; else fail "/ping.txt attendu 'pong' (HTTP $CODE, body: $(echo "$BODY" | tr -d '\n' | head -c 120)...)"; fi

# 2) /
title "Test / (index SPA contenant 'ugo')"
curl_capture GET "${BASE}/"
if echo "$BODY" | grep -iq 'ugo'; then pass "/ contient 'ugo' (HTTP $CODE)"; else fail "/ n'inclut pas 'ugo' (HTTP $CODE, body: $(echo "$BODY" | tr -d '\n' | head -c 120)...)"; fi

# helpers
test_variant_get() {
  local label="$1" url="$2"
  curl_capture GET "$url"
  if [[ "$CODE" =~ ^2 ]]; then
    if has_cmd jq && echo "$BODY" | jq . >/dev/null 2>&1; then pass "$label → 2xx + JSON"
    else pass "$label → 2xx (body $(printf %s "$BODY" | tr -d '\n' | head -c 80)...)"
    fi
    return 0
  else
    echo "… $label → HTTP $CODE (head: $(echo "$HDR" | tr -d '\n' | head -c 80) | body: $(echo "$BODY" | tr -d '\n' | head -c 100)...)"
    return 1
  fi
}

test_variant_post() {
  local label="$1" url="$2" data="$3"
  curl_capture POST "$url" "$data"
  if [[ "$CODE" =~ ^2 ]]; then
    if has_cmd jq && echo "$BODY" | jq . >/dev/null 2>&1; then pass "$label → 2xx + JSON"
    else pass "$label → 2xx (body $(printf %s "$BODY" | tr -d '\n' | head -c 80)...)"
    fi
    return 0
  else
    echo "… $label → HTTP $CODE (head: $(echo "$HDR" | tr -d '\n' | head -c 80) | body: $(echo "$BODY" | tr -d '\n' | head -c 100)...)"
    return 1
  fi
}

# 3) health (GET) — variantes
title "API non persistante: GET /api/health (avec variantes)"
HOK=0
test_variant_get "GET /api/health"        "${BASE}/api/health"        || true
test_variant_get "GET /api/health/ "      "${BASE}/api/health/"       || true
test_variant_get "GET /api/index.php/health" "${BASE}/api/index.php/health" || true
# succès si au moins une variante passe
if grep -q "→ 2xx" <(printf "%s\n" "$(test_variant_get "dry" "${BASE}/api/health" 2>/dev/null || true)") ; then :; fi # noop
if [[ $OK -gt 2 ]]; then :; else fail "Aucune variante GET /api/health n'a répondu 2xx"; fi

# 4) filterMissing (POST) — variantes
title "API non persistante: POST /api/filterMissing (avec variantes)"
PAYLOAD='{"externalIds":["yt:abc123","fb:999","yt:xyz777"],"existingIds":["yt:abc123"]}'
BOK_BEFORE=$OK
test_variant_post "POST /api/filterMissing"           "${BASE}/api/filterMissing"           "$PAYLOAD" || true
test_variant_post "POST /api/filterMissing/"          "${BASE}/api/filterMissing/"          "$PAYLOAD" || true
test_variant_post "POST /api/index.php/filterMissing" "${BASE}/api/index.php/filterMissing" "$PAYLOAD" || true
if [[ $OK -gt $BOK_BEFORE ]]; then :; else fail "Aucune variante POST /api/filterMissing n'a répondu 2xx"; fi

# SKIP writes
title "API potentiellement persistantes — SKIP"
skip "POST /api/batchUpsert (écrit DB)"
skip "POST /api/metrics* (ingest/compute — écrit DB)"

echo; echo "${BOLD}Résumé:${RESET} ${GREEN}${OK} OK${RESET}  ${RED}${FAIL} FAIL${RESET}  ${YELLOW}${SKIP} SKIP${RESET}"
[[ $FAIL -eq 0 ]] || exit 1
