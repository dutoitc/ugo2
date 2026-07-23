# UGO2 Web

Le dossier `web/` contient l’API PHP, les sources Angular, le build servi statiquement et les scripts SQL.

## Arborescence

| Chemin | Contenu |
|---|---|
| `.htaccess` | Route l’API vers PHP et les autres URL vers la SPA. |
| `src/back/` | PHP 8, contrôleurs, services et accès PDO. |
| `src/front/` | Build Angular généré localement et ignoré par Git. |
| `ihm/` | Sources Angular. |
| `sql/` | Bootstrap SQL historique. |
| `doc/` | Contrats API et modèle DB. |

Le docroot du serveur web doit être ce dossier. `.htaccess` bloque l’accès direct à `src/back`, `sql` et `doc`, puis route `/api/*` vers `index.php`.

## Configuration locale

```bash
cp src/back/config/config.php.tmpl src/back/config/config.php
```

Renseigner la connexion MariaDB et les clés HMAC uniquement dans `config.php`, qui n’est pas versionné. Le modèle conserve exclusivement des exemples neutres.

Pour utiliser une autre API avec le serveur Angular local :

```bash
cp ihm/src/proxy.conf.json ihm/src/proxy.conf.local.json
```

Modifier uniquement `proxy.conf.local.json`, ignoré par Git, puis lancer `ihm/myserve.sh`.

## Build frontend

```bash
cd ihm
npm ci
npm audit --omit=dev --audit-level=moderate
npm run build
```

Le résultat remplace les fichiers générés de `src/front/`. Ces fichiers ne doivent pas être commités.

## Déploiement

Créer une configuration locale à partir du modèle :

```bash
cp site.properties.tmpl site.properties
./up.sh site.properties
```

`up.sh` reconstruit Angular, prépare la configuration PHP choisie et transfère le site par SSH/rsync. La suppression des anciens fichiers distants est désactivée par défaut ; l’activer explicitement avec `DELETE_REMOTE=1` dans la configuration locale.

## Base de données

Les scripts `sql/001` à `008` sont un bootstrap historique pour une base vide. Ils ne constituent pas des migrations de production : certains suppriment ou recréent des tables et vues. Ne jamais les rejouer sur une base contenant des données ; sauvegarder et examiner le SQL avant toute initialisation.

Voir [`doc/db.md`](doc/db.md) pour les objets réellement utilisés.

## Vérification non destructive

Après avoir démarré une instance locale ou de test :

```bash
./testui.sh http://localhost:8080
```

Le script ne fait que trois lectures : la SPA, `/api/v1/health` et `/api/v1/videos`.
