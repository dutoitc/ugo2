#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   DRY_RUN=1 ./up.sh site.properties   # simulation (affiche + rsync -n)
#   ./up.sh site.properties             # exécution
#
# .properties requis: HOST, PORT, USER, REMOTE_DIR, CONFIG_VARIANT
# Optionnel: SSH_IDENTITY=~/.ssh/id_ed25519 (clé PRIVÉE, pas .pub)

trim() { local s="${1//$'\r'/}"; s="${s#"${s%%[![:space:]]*}"}"; s="${s%"${s##*[![:space:]]}"}"; printf '%s' "$s"; }
join_cmd() { printf "%q " "$@"; }

load_props() {
  local file="$1"; [[ -f "$file" ]] || { echo "Config introuvable: $file" >&2; exit 2; }
  declare -g HOST="" PORT="" USER="" REMOTE_DIR="" CONFIG_VARIANT="" SSH_IDENTITY="" DRY_RUN="${DRY_RUN:-0}"
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"; line="$(trim "$line")"
    [[ -z "$line" || "${line:0:1}" == "#" || "$line" != *=* ]] && continue
    local key="$(trim "${line%%=*}")"; local val="$(trim "${line#*=}")"
    if [[ ( "${val:0:1}" == "'" && "${val: -1}" == "'" ) || ( "${val:0:1}" == '"' && "${val: -1}" == '"' ) ]]; then
      val="${val:1:${#val}-2}"
    fi
    case "$key" in HOST|PORT|USER|REMOTE_DIR|CONFIG_VARIANT|SSH_IDENTITY|DRY_RUN) printf -v "$key" '%s' "$val" ;; esac
  done < "$file"
}

expand_local_path() {  # ~ → $HOME
  local p="$1"
  if [[ "$p" == "~" ]]; then printf '%s\n' "$HOME"; return; fi
  if [[ "$p" == "~/"* ]]; then printf '%s/%s\n' "$HOME" "${p#~/}"; return; fi
  printf '%s\n' "$p"
}

if [[ $# -ne 1 ]]; then echo "Usage: $0 <config.properties>" >&2; exit 2; fi
CONF="$1"; load_props "$CONF"

: "${HOST:?HOST manquant}"; : "${PORT:?PORT manquant}"; : "${USER:?USER manquant}"
: "${REMOTE_DIR:?REMOTE_DIR manquant}"; : "${CONFIG_VARIANT:?CONFIG_VARIANT manquant}"

command -v rsync >/dev/null || { echo "rsync introuvable"; exit 4; }
command -v ssh   >/dev/null || { echo "ssh introuvable";   exit 5; }

cd "$(dirname "$0")"  # -> web/

# Config PHP effective
[[ -f "src/back/config/${CONFIG_VARIANT}" ]] || { echo "Variant introuvable: src/back/config/${CONFIG_VARIANT}" >&2; exit 6; }
cp -f "src/back/config/${CONFIG_VARIANT}" "src/back/config/config.php"

# SSH opts
SSH_OPTS=(-p "$PORT" -o StrictHostKeyChecking=accept-new)
if [[ -n "${SSH_IDENTITY:-}" ]]; then
  SSH_IDENTITY="$(expand_local_path "$SSH_IDENTITY")"
  SSH_OPTS+=(-i "$SSH_IDENTITY")
fi

# REMOTE_DIR : accepte ~/... (expansion côté serveur) OU chemin absolu /...
REMOTE_IS_TILDE=0
if [[ "$REMOTE_DIR" == "~/"* ]]; then
  REMOTE_IS_TILDE=1
else
  if [[ "${REMOTE_DIR:0:1}" != "/" ]] || [[ "$REMOTE_DIR" == "/" ]] || [[ ${#REMOTE_DIR} -lt 5 ]]; then
    echo "REMOTE_DIR invalide: '$REMOTE_DIR' (abs. requis, ex: /sites/ugo2.capstv.ch)" >&2; exit 3
  fi
fi
[[ "${REMOTE_DIR: -1}" == "/" ]] || REMOTE_DIR="${REMOTE_DIR}/"

echo "== Config =="
echo " Host       : $HOST"
echo " User       : $USER"
echo " Port       : $PORT"
echo " Remote dir : $REMOTE_DIR"
echo " SSH key    : ${SSH_IDENTITY:-<none>}"
echo " DRY_RUN    : ${DRY_RUN:-0}"
echo

# mkdir -p distant
if [[ "$REMOTE_IS_TILDE" -eq 1 ]]; then
  suffix="${REMOTE_DIR#~/}"
  MKDIR_CMD=(ssh "${SSH_OPTS[@]}" "${USER}@${HOST}" "cd ~ && mkdir -p -- '$(printf "%s" "$suffix" | sed "s/'/'\\\\''/g")'")
else
  MKDIR_CMD=(ssh "${SSH_OPTS[@]}" "${USER}@${HOST}" "mkdir -p -- '$(printf "%s" "$REMOTE_DIR" | sed "s/'/'\\\\''/g")'")
fi
if [[ "${DRY_RUN:-0}" == "1" ]]; then
  echo "[DRY-RUN] $(join_cmd "${MKDIR_CMD[@]}")"
else
  "${MKDIR_CMD[@]}"
fi

# Filtres rsync :
#  - PROTÈGE côté serveur: .infomaniak-maintenance.html et .user.ini (ne pas supprimer)
#  - Les mêmes sont EXCLUS du transfert (on ne les touche pas)
#  - Exclusions sensibles avant include large
FILTERS=(
  "--filter=protect /.infomaniak-maintenance.html"
  "--filter=protect /.user.ini"
  "--exclude=/.infomaniak-maintenance.html"
  "--exclude=/.user.ini"
  "--exclude=/src/back/config/config.php.*"
  "--exclude=/src/back/config/*.tmpl"
  "--exclude=/.git/"
  "--exclude=/.idea/"
  "--exclude=/.vscode/"
  "--exclude=*.zip"
  "--exclude=*.tgz"
  "--exclude=*.tar.gz"
  "--include=/src/***"
  "--include=/.htaccess"
  "--include=/index.php"
  "--include=/chk.php"
  "--exclude=*"
)

RSYNC_SSH="ssh -p $PORT -o StrictHostKeyChecking=accept-new"
[[ -n "${SSH_IDENTITY:-}" ]] && RSYNC_SSH+=" -i $(printf "%q" "$SSH_IDENTITY")"

RS_OPTS=(
  -az
  --delete
  --delete-excluded
  --prune-empty-dirs
  --info=stats2
  --chmod=Du=rwx,Dg=rx,Do=rx,Fu=rw,Fg=r,Fo=r
  -e "$RSYNC_SSH"
)
[[ "${DRY_RUN:-0}" == "1" ]] && RS_OPTS+=( -n -v )

RSYNC_CMD=(rsync "${RS_OPTS[@]}" "${FILTERS[@]}" ./ "${USER}@${HOST}:${REMOTE_DIR}")
echo "[CMD] $(join_cmd "${RSYNC_CMD[@]}")"
"${RSYNC_CMD[@]}"

echo "✔ Terminé."
