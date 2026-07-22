# UGO2 Web

Le dossier `web/` contient l’API PHP, le build Angular servi statiquement et les scripts SQL.

## Arborescence

| Chemin | Contenu |
|---|---|
| `index.php` | Point d’entrée de l’API et fallback de la SPA. |
| `src/back/` | PHP 8, contrôleurs, services et accès PDO. |
| `src/front/` | Build Angular généré. |
| `ihm/` | Sources Angular. |
| `sql/` | Bootstrap SQL historique. |
| `doc/` | Contrats API et modèle DB. |

Le docroot du serveur web doit être ce dossier. `.htaccess` bloque l’accès direct à `src/back`, `sql` et `doc`, puis route `/api/*` vers `index.php`.

## Configuration locale

```bash
cp src/back/config/config.php.tmpl src/back/config/config.php
```

Renseigner la connexion MariaDB et les clés HMAC uniquement dans `config.php`, qui n’est pas versionné. Le modèle conserve exclusivement des exemples neutres.

## Build frontend

```bash
cd ihm
npm ci
npm audit --omit=dev --audit-level=moderate
npm run build
```

Le résultat remplace les fichiers générés de `src/front/`.

## Base de données

Les scripts `sql/001` à `008` sont un bootstrap historique pour une base vide. Ils ne constituent pas des migrations de production : certains suppriment ou recréent des tables et vues. Ne jamais les rejouer sur une base contenant des données ; sauvegarder et examiner le SQL avant toute initialisation.

Voir [`doc/db.md`](doc/db.md) pour les objets réellement utilisés.

## Vérification non destructive

Après avoir démarré une instance locale ou de test :

```bash
./testui.sh http://localhost:8080
```

Le script ne fait que trois lectures : la SPA, `/api/v1/health` et `/api/v1/videos`. Aucun déploiement distant n’est automatisé par ce dépôt.
