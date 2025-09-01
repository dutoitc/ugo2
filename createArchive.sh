#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob dotglob

ARCHIVE="ugo2-$(date +%Y%m%d-%H%M%S).tgz"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

# --- Mode 1: Git (idé) -> archive uniquement des fichiers suivis
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git ls-files -z | tar --no-recursion --null -T - -czf "$TMPDIR/$ARCHIVE"
  mv "$TMPDIR/$ARCHIVE" "./$ARCHIVE"
  printf 'done: %s\n' "$ARCHIVE"
  exit 0
fi

# --- Mode 2: fallback (sans Git) avec exclusions solides
EXCLUDES=(
  --exclude="$ARCHIVE"
  --exclude="*.tgz"
  --exclude="*.tar.gz"
  --exclude=".git"         --exclude="./.git"         --exclude="*/.git/*"
  --exclude=".idea"        --exclude="./.idea"        --exclude="*/.idea/*"
  --exclude=".vscode"      --exclude="./.vscode"      --exclude="*/.vscode/*"
  --exclude="node_modules" --exclude="./node_modules" --exclude="*/node_modules/*"
  --exclude="target"       --exclude="./target"       --exclude="*/target/*"
  --exclude="build"        --exclude="./build"        --exclude="*/build/*"
  --exclude="dist"         --exclude="./dist"         --exclude="*/dist/*"
  --exclude=".DS_Store"
)

# snapshot des fichiers (inclut dotfiles, pas . ni ..)
FILES=( * .[!.]* ..?* )

# éire l'archive hors du dossier courant pour éter l'auto-inclusion
tar -czf "$TMPDIR/$ARCHIVE" "${EXCLUDES[@]}" "${FILES[@]}"

mv "$TMPDIR/$ARCHIVE" "./$ARCHIVE"
printf 'done: %s\n' "$ARCHIVE"

