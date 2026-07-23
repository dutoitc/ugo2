#!/usr/bin/env bash
set -euo pipefail

# Usage :
#   cp site.properties.tmpl site.properties
#   DRY_RUN=1 ./up.sh site.properties
#   ./up.sh site.properties
#
# Le fichier .properties réel reste local et est ignoré par Git.

trim() {
  local value="${1//$'\r'/}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

join_cmd() {
  printf "%q " "$@"
}

load_props() {
  local file="$1"
  [[ -f "$file" ]] || { echo "Config introuvable : $file" >&2; exit 2; }

  declare -g HOST="" PORT="" DEPLOY_USER="" REMOTE_DIR="" CONFIG_VARIANT=""
  declare -g SSH_IDENTITY="" DRY_RUN="${DRY_RUN:-0}" DELETE_REMOTE="${DELETE_REMOTE:-0}"

  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    line="$(trim "$line")"
    [[ -z "$line" || "${line:0:1}" == "#" || "$line" != *=* ]] && continue

    local key
    local value
    key="$(trim "${line%%=*}")"
    value="$(trim "${line#*=}")"
    if [[ ( "${value:0:1}" == "'" && "${value: -1}" == "'" ) ||
          ( "${value:0:1}" == '"' && "${value: -1}" == '"' ) ]]; then
      value="${value:1:${#value}-2}"
    fi

    case "$key" in
      HOST|PORT|REMOTE_DIR|CONFIG_VARIANT|SSH_IDENTITY|DRY_RUN|DELETE_REMOTE)
        printf -v "$key" '%s' "$value"
        ;;
      USER)
        DEPLOY_USER="$value"
        ;;
    esac
  done < "$file"
}

expand_local_path() {
  local path="$1"
  if [[ "$path" == "~" ]]; then
    printf '%s\n' "$HOME"
    return
  fi
  if [[ "$path" == "~/"* ]]; then
    printf '%s/%s\n' "$HOME" "${path#~/}"
    return
  fi
  printf '%s\n' "$path"
}

if [[ $# -ne 1 ]]; then
  echo "Usage : $0 <config.properties>" >&2
  exit 2
fi

CONF="$1"
load_props "$CONF"

: "${HOST:?HOST manquant}"
: "${PORT:?PORT manquant}"
: "${DEPLOY_USER:?USER manquant}"
: "${REMOTE_DIR:?REMOTE_DIR manquant}"
: "${CONFIG_VARIANT:?CONFIG_VARIANT manquant}"
[[ "$DRY_RUN" == "0" || "$DRY_RUN" == "1" ]] ||
  { echo "DRY_RUN doit valoir 0 ou 1" >&2; exit 2; }
[[ "$DELETE_REMOTE" == "0" || "$DELETE_REMOTE" == "1" ]] ||
  { echo "DELETE_REMOTE doit valoir 0 ou 1" >&2; exit 2; }

command -v npm >/dev/null || { echo "npm introuvable" >&2; exit 4; }
command -v rsync >/dev/null || { echo "rsync introuvable" >&2; exit 5; }
command -v ssh >/dev/null || { echo "ssh introuvable" >&2; exit 6; }

cd "$(dirname "$0")"

# Le build est généré localement dans src/front/ et n'est jamais versionné.
(cd ihm && npm run build)

[[ -f "src/back/config/${CONFIG_VARIANT}" ]] ||
  { echo "Variante introuvable : src/back/config/${CONFIG_VARIANT}" >&2; exit 7; }
cp -f "src/back/config/${CONFIG_VARIANT}" "src/back/config/config.php"

SSH_OPTS=(-p "$PORT" -o StrictHostKeyChecking=accept-new)
if [[ -n "$SSH_IDENTITY" ]]; then
  SSH_IDENTITY="$(expand_local_path "$SSH_IDENTITY")"
  SSH_OPTS+=(-i "$SSH_IDENTITY")
fi

REMOTE_IS_TILDE=0
if [[ "$REMOTE_DIR" == "~/"* ]]; then
  REMOTE_IS_TILDE=1
elif [[ "${REMOTE_DIR:0:1}" != "/" || "$REMOTE_DIR" == "/" || ${#REMOTE_DIR} -lt 5 ]]; then
  echo "REMOTE_DIR invalide : '$REMOTE_DIR'" >&2
  exit 3
fi
[[ "${REMOTE_DIR: -1}" == "/" ]] || REMOTE_DIR="${REMOTE_DIR}/"

echo "== Config =="
echo " Host              : $HOST"
echo " User              : $DEPLOY_USER"
echo " Port              : $PORT"
echo " Remote dir        : $REMOTE_DIR"
echo " SSH key           : ${SSH_IDENTITY:-<none>}"
echo " Dry run           : $DRY_RUN"
echo " Suppression remote: $DELETE_REMOTE"
echo

if [[ "$REMOTE_IS_TILDE" -eq 1 ]]; then
  suffix="${REMOTE_DIR#~/}"
  escaped_suffix="$(printf "%s" "$suffix" | sed "s/'/'\\\\''/g")"
  MKDIR_CMD=(ssh "${SSH_OPTS[@]}" "${DEPLOY_USER}@${HOST}" "cd ~ && mkdir -p -- '$escaped_suffix'")
else
  escaped_remote="$(printf "%s" "$REMOTE_DIR" | sed "s/'/'\\\\''/g")"
  MKDIR_CMD=(ssh "${SSH_OPTS[@]}" "${DEPLOY_USER}@${HOST}" "mkdir -p -- '$escaped_remote'")
fi

if [[ "$DRY_RUN" == "1" ]]; then
  echo "[DRY-RUN] $(join_cmd "${MKDIR_CMD[@]}")"
else
  "${MKDIR_CMD[@]}"
fi

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
  "--include=/README.md"
  "--exclude=*"
)

RSYNC_SSH="ssh -p $PORT -o StrictHostKeyChecking=accept-new"
[[ -n "$SSH_IDENTITY" ]] && RSYNC_SSH+=" -i $(printf "%q" "$SSH_IDENTITY")"

RS_OPTS=(
  -az
  --prune-empty-dirs
  --info=stats2
  --chmod=Du=rwx,Dg=rx,Do=rx,Fu=rw,Fg=r,Fo=r
  -e "$RSYNC_SSH"
)
[[ "$DELETE_REMOTE" == "1" ]] && RS_OPTS+=(--delete-delay --delete-excluded)
[[ "$DRY_RUN" == "1" ]] && RS_OPTS+=(-n -v)

RSYNC_CMD=(rsync "${RS_OPTS[@]}" "${FILTERS[@]}" ./ "${DEPLOY_USER}@${HOST}:${REMOTE_DIR}")
echo "[CMD] $(join_cmd "${RSYNC_CMD[@]}")"
"${RSYNC_CMD[@]}"

echo "Terminé."
