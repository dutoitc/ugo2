# UGO2 — suivi multi-plateformes des vidéos

UGO2 agrège les statistiques de vidéos publiées sur **YouTube**, **Facebook** et **Instagram**, puis rapproche les publications correspondant à une même vidéo. L’objectif est de suivre deux webTV indépendantes sans mélanger leurs données.

## Architecture

```text
APIs plateformes
      |
      v
Batch Java 21 / Spring Boot
      |
      v
API PHP 8 + MariaDB
      |
      v
IHM Angular
```

- **Une base et une configuration par webTV**.
- Le batch ne se connecte pas directement à MariaDB : il pousse sources et métriques vers l’API PHP.
- Le front Angular lit l’API et affiche la liste, le détail, les graphes et les doublons.
- Documentation détaillée : [`ARCHITECTURE.md`](ARCHITECTURE.md).
- Structure DB : [`web/doc/db.md`](web/doc/db.md).

## Données principales

- `video` : vidéo canonique commune aux plateformes.
- `source_video` : publication YouTube/Facebook/Instagram/TikTok.
- `metric_snapshot` : métriques cumulées à un instant donné.
- vues et tables analytiques : derniers états, agrégats et séries temporelles.

Les vues cumulées doivent rester monotones. Une valeur absente doit rester `null` et ne doit pas être transformée en zéro.

## Développement

### Batch

Prérequis : JDK 21 et Maven récent.

```bash
mvn test
mvn -pl batch package
java -jar batch/target/batch.jar batch:run
```

Créer la configuration réelle à partir de :

```text
batch/src/main/resources/application.properties.tmpl
```

Le fichier réel reste hors Git.

### IHM Angular

```bash
cd web/ihm
npm ci
npm run build
```

Le build généré est déployé dans `web/src/front/`; il n’est pas utile dans les archives de revue de code.

### API PHP

Prérequis : PHP 8.x et MariaDB.

```bash
find web/src/back -name '*.php' -print0 | xargs -0 -n1 php -l
```

La configuration réelle est créée depuis `web/src/back/config/config.php.tmpl` et reste hors Git.

## Archive de revue

Depuis la racine du dépôt :

```bash
./createArchive.sh
```

L’archive contient `files.txt`, les sources, les tests, les fichiers SQL et la documentation. Elle exclut les builds, médias, IDE et configurations réelles.

## Priorités

Voir [`TODO.md`](TODO.md) pour les points MUST/SHOULD/DO : sécurité HMAC, monotonie des vues, assainissement DB, parcimonie des snapshots, performances et améliorations IHM.
