#!/usr/bin/env bash
set -euo pipefail
# === Config par site (duplique ce fichier pour chaque site) ===
REMOTE_USER="USER_REPLACE_ME"
REMOTE_HOST="ssh-XX.srv.com"
REMOTE_DIR="/home/clients/XXXXXXXX/web"   # DocumentRoot
CONFIG_VARIANT="config.php.site1"         # présent dans src/back/config/

cd "$(dirname "$0")"

# 1) Sélectionne la config locale à déployer
if [[ ! -f "src/back/config/${CONFIG_VARIANT}" ]]; then
  echo "Config variant src/back/config/${CONFIG_VARIANT} introuvable"; exit 1
fi
cp -f "src/back/config/${CONFIG_VARIANT}" "src/back/config/config.php"

# 2) Déploiement via rsync (SSH). Nécessite un accès SSH activé.
rsync -avz --delete       --exclude='.git/'       --exclude='*.zip'       --exclude='src/back/config/config.php.*'       --exclude='src/back/config/config.php.tmpl'       ./ "${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}/"

echo "OK: push terminé vers ${REMOTE_USER}@${REMOTE_HOST}:${REMOTE_DIR}"
