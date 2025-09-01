#!/usr/bin/env bash
set -euo pipefail

ARCHIVE="${1:-ugo2-$(date +%Y%m%d-%H%M%S).tgz}"

# Exclusions par décompat GNU tar & bsdtar)
EXCLUDES=(
  "--exclude=$ARCHIVE"
  "--exclude=.git"
  "--exclude=.idea"
  "--exclude=.vscode"
  "--exclude=node_modules"
  "--exclude=target"
  "--exclude=build"
  "--exclude=dist"
  "--exclude=.DS_Store"
  "--exclude=*.tgz"
  "--exclude=*.tar.gz"
)

# Tar en racine du dossier courant
tar czf "$ARCHIVE" \
  "${EXCLUDES}" \
  *

echo "done: $ARCHIVE"



