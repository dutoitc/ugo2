# UGO2 Web (single-root hosting)

- Docroot = `web/`
- API sous `/api/*` (routé vers `index.php`)
- Code serveur: `src/back/` (bloqué par `.htaccess`), Config: `src/back/config/`
- Front: `src/front/` (HTML/CSS/JS)

## Déploiement
1) Copier `src/back/config/config.php.tmpl` -> `src/back/config/config.php.site1` (et `.site2` etc.), remplir DB + clés HMAC.
2) Importer SQL: `sql/001_schema.sql`, `002_views.sql`, `003_indexes.sql`.
3) Éditer en haut de `up.sh` (utilisateur/host/dir + CONFIG_VARIANT), rendre exécutable `chmod +x up.sh`.
4) `./up.sh`

## Nettoyage avant mise à jour
Si tu viens d'un ancien layout:
  - supprime `web/public/` et `web/src/` existants
  - supprime `web/index.php`, `web/.htaccess` si présents (ils seront recréés)
  - supprime `web/sql/` (sera recréé)

Commande rapide (Git Bash) :
rm -rf web/public web/src web/sql; rm -f web/index.php web/.htaccess web/README.md
