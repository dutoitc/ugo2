#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT"

ARCHIVE="${ARCHIVE:-ugo2-$(date +%Y%m%d-%H%M%S).tgz}"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

CANDIDATES="$TMPDIR/candidates.txt"
MANIFEST="$TMPDIR/files.txt"
MANIFEST_NUL="$TMPDIR/files.nul"
STAGE="$TMPDIR/stage"
mkdir -p "$STAGE"

is_allowed_path() {
  case "$1" in
    .gitignore|CHANGELOG.md|LICENSE|README.md|REQUIREMENTS.md|SCHEDULING.md|TODO.md|ARCHITECTURE.md|spec-ihm.md|pom.xml|createArchive.sh)
      return 0 ;;
    .github/*|batch/*|config/*|scripts/*|web/*)
      return 0 ;;
    *)
      return 1 ;;
  esac
}

is_excluded_path() {
  case "$1" in
    *.tgz|*.tar.gz|*.zip|*.rar|*.7z|*.sql.gz|*.dump|*.bak|*.sqlite|*.sqlite3|*.db|*.csv|*.xlsx|*.xls|*.log|*.patch|files.txt)
      return 0 ;;
    *.sql)
      [[ "$1" == web/sql/*.sql ]] && return 1
      return 0 ;;
    .git/*|*/.git/*|.idea/*|*/.idea/*|.vscode/*|*/.vscode/*)
      return 0 ;;
    node_modules/*|*/node_modules/*|target/*|*/target/*|dist/*|*/dist/*|build/*|*/build/*|coverage/*|*/coverage/*|.angular/*|*/.angular/*)
      return 0 ;;
    web/src/front/*)
      return 0 ;;
    *.png|*.jpg|*.jpeg|*.gif|*.webp|*.bmp|*.ico|*.mp4|*.mov|*.avi|*.mkv|*.mp3|*.wav|*.flac)
      return 0 ;;
    *.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|*.htpasswd|id_rsa|id_rsa.*|id_ed25519|id_ed25519.*|.env|.env.*|*/.env|*/.env.*)
      return 0 ;;
    config/application*.yml|config/application*.yaml|config/application*.properties)
      [[ "$1" == *.tmpl ]] && return 1
      return 0 ;;
    batch/src/main/resources/application*.properties|batch/src/main/resources/application*.yml|batch/src/main/resources/application*.yaml)
      [[ "$1" == *.tmpl ]] && return 1
      return 0 ;;
    web/site*.properties)
      [[ "$1" == *.tmpl ]] && return 1
      return 0 ;;
    web/src/back/config/config.php|web/src/back/config/config.php.*)
      [[ "$1" == *.tmpl ]] && return 1
      return 0 ;;
    web/up-capstv*|web/up-xplore*|web/myconnectweb.sh|web/testui-capstv.sh)
      return 0 ;;
    *)
      return 1 ;;
  esac
}

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  # Inclut les fichiers suivis et les nouveaux fichiers non ignorés du working tree.
  git ls-files --cached --others --exclude-standard -z |
    while IFS= read -r -d '' path; do
      path="${path#./}"
      is_allowed_path "$path" || continue
      is_excluded_path "$path" && continue
      [[ -f "$path" || -L "$path" ]] && printf '%s\n' "$path"
    done > "$CANDIDATES"
else
  find . -type f -print0 |
    while IFS= read -r -d '' path; do
      path="${path#./}"
      is_allowed_path "$path" || continue
      is_excluded_path "$path" && continue
      printf '%s\n' "$path"
    done > "$CANDIDATES"
fi

LC_ALL=C sort -u "$CANDIDATES" > "$MANIFEST"
[[ -s "$MANIFEST" ]] || { echo "Aucun fichier utile trouvé" >&2; exit 2; }

while IFS= read -r path; do
  printf '%s\0' "$path"
done < "$MANIFEST" > "$MANIFEST_NUL"

# Copie exacte de la sélection dans un répertoire temporaire, puis ajoute le manifeste.
tar -C "$ROOT" --null --no-recursion -T "$MANIFEST_NUL" -cf - | tar -C "$STAGE" -xf -
cp "$MANIFEST" "$STAGE/files.txt"
tar -C "$STAGE" -czf "$TMPDIR/$ARCHIVE" .

mv "$TMPDIR/$ARCHIVE" "$ROOT/$ARCHIVE"
printf 'done: %s (%s fichiers)\n' "$ARCHIVE" "$(wc -l < "$MANIFEST" | tr -d ' ')"
printf 'manifest: files.txt inclus dans l\047archive\n'
