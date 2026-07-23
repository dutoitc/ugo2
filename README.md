# UGO2 — statistiques vidéo multi-plateformes

UGO2 collecte des métadonnées et métriques YouTube, Facebook et Instagram, rapproche les publications d’une même vidéo, puis les restitue dans une interface web.

## Composants

| Dossier | Technologie | Rôle |
|---|---|---|
| `batch/` | Java 21, Spring Boot, Maven | Collecte les plateformes et envoie sources, métriques et santé à l’API. |
| `web/src/back/` | PHP 8, PDO | Expose l’API JSON, réconcilie les sources et lit/écrit MariaDB. |
| `web/ihm/` | Angular 20, ECharts 6 | Affiche vidéos, détails, graphes, doublons et santé. |
| `web/sql/` | MariaDB | Schéma historique, vues, index et structures analytiques. |

Le batch n’accède jamais directement à MariaDB. Une instance se compose d’une configuration batch, d’une configuration PHP et d’une base distinctes.

> L’authentification HMAC existe côté client et dans `Auth.php`, mais elle n’est pas encore imposée par les contrôleurs PHP. Les routes d’écriture ne doivent donc pas être exposées publiquement avant le MUST correspondant dans [`TODO.md`](TODO.md).

## Configuration

Copier les modèles puis remplacer uniquement les valeurs localement :

```bash
cp batch/src/main/resources/application.properties.tmpl \
   batch/src/main/resources/application.properties
cp web/src/back/config/config.php.tmpl \
   web/src/back/config/config.php
```

Ces deux fichiers réels sont ignorés par Git. Ne jamais y substituer des secrets dans les modèles, les logs ou les archives.

## Développement et validation

Prérequis : JDK 21, Maven, PHP 8 avec PDO MySQL, Node.js 22/npm et MariaDB.

```bash
mvn test

find web/src/back -name '*.php' -print0 | xargs -0 -n1 php -l

cd web/ihm
npm ci
npm audit --omit=dev --audit-level=moderate
npm run build
```

Le build Angular est écrit dans `web/src/front/`, qui est ignoré par Git : le dépôt ne contient que les sources de `web/ihm/`. Le workflow GitHub `frontend.yml` exécute l’audit des dépendances de production et le build. `secret-scan.yml` analyse l’historique Git ainsi qu’une archive de revue.

## Base de données

Les scripts `web/sql/001` à `008` décrivent le bootstrap historique. Certains suppriment ou recréent des objets : ils sont réservés à une base vide et ne doivent jamais être rejoués sur une base remplie. Les migrations versionnées et réexécutables restent un MUST.

La structure actuellement utilisée est décrite dans [`web/doc/db.md`](web/doc/db.md).

## Documentation

- [Architecture](ARCHITECTURE.md)
- [API PHP](web/doc/api.md)
- [Base de données](web/doc/db.md)
- [IHM implémentée](spec-ihm.md)
- [Modèle de configuration batch](batch/src/main/resources/application.properties.tmpl)
- [Travaux restants](TODO.md)

Le dépôt est distribué selon la licence fournie dans [`LICENSE`](LICENSE).
