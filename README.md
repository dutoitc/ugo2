# UGO2 — suivi multi-plateformes des vidéos

UGO2 agrège les statistiques de vidéos publiées sur **YouTube**, **Facebook** et **Instagram**, puis rapproche les publications correspondant à une même vidéo. L’objectif est de suivre deux webTV indépendantes sans mélanger leurs données.

## Architecture

```text
APIs YouTube / Facebook / Instagram
                 |
                 v
       Batch Java 21 / Spring Boot
       collecte + rapport de santé
                 |
                 v
          API PHP 8 + MariaDB
  réconciliation + snapshots parcimonieux
                 |
                 v
              Angular
       listes, graphes et santé
```

- Une base et une configuration par webTV.
- Le batch ne se connecte pas directement à MariaDB : il pousse sources et métriques vers l’API PHP.
- Le client batch signe les écritures en HMAC. L’imposition systématique côté serveur reste suivie dans [`TODO.md`](TODO.md).
- Documentation détaillée : [`ARCHITECTURE.md`](ARCHITECTURE.md).
- Structure DB : [`web/doc/db.md`](web/doc/db.md).

Les secrets restent uniquement dans les fichiers réels non versionnés :

- `batch/src/main/resources/application.properties` ;
- `web/src/back/config/config.php` ;
- les configurations de déploiement hors Git.

Ne jamais versionner de token, clé API, mot de passe ou export réel de base.

## Base de données

Appliquer les scripts `web/sql` dans l’ordre numérique. Pour cette évolution :

```bash
mysql -u USER -p DBNAME < web/sql/008_health_sparse_refresh.sql
```

Tables principales :

- `video` : vidéo canonique commune aux plateformes ;
- `source_video` : publication YouTube/Facebook/Instagram/TikTok ;
- `metric_snapshot` : métriques cumulées à un instant donné ;
- `mv_video_rollup` : état agrégé utilisé par la liste et les tris ;
- `platform_health` : dernier succès, erreur, fraîcheur et état du token ;
- `batch_run` : durée et résultat des exécutions ;
- `refresh_job_state` : refresh analytique différé, verrouillé et mesuré.

## Intégrité et parcimonie

Lors de l’ingestion :

- `views_native` et `total_watch_seconds` ne peuvent pas diminuer ;
- `null` signifie « inconnue » et ne remplace pas une valeur connue ;
- un snapshot est conservé au premier point, pour un delta significatif de vues, un changement d’engagement utile ou le point quotidien ;
- les vues matérialisées sont marquées comme sales pendant l’ingestion puis recalculées une seule fois à la fin de `batch:run` ;
- le refresh utilise un verrou MariaDB et enregistre sa durée et sa dernière erreur.

Configuration PHP indicative :

```php
'metrics' => [
  'minDeltaAbs' => 10,
  'minDeltaRel' => 0.01,
  'dailyGuard' => true,
],
'health' => [
  'staleWarningHours' => 26,
  'staleCriticalHours' => 72,
  'tokenWarningDays' => 14,
],
```

## Build et commandes

Prérequis : JDK 21, Maven récent, PHP 8.x, Node/npm et MariaDB.

```bash
mvn test
mvn -pl batch package
java -jar batch/target/batch.jar sanity:check
java -jar batch/target/batch.jar batch:run

cd web/ihm
npm ci
npm run build

find ../src/back -name '*.php' -print0 | xargs -0 -n1 php -l
```

| Commande | Usage | Durée indicative pour environ 200 vidéos |
|---|---|---:|
| `sanity:check` | vérifie `/health` et l’accès API | 1–5 s |
| `sanity:sources` | envoie une source factice, développement uniquement | 1–5 s |
| `batch:run` | collecte YT/FB/IG, réconcilie et rafraîchit une fois | **2–8 min** |
| refresh analytique inclus | rollup et séries des graphes | 5–60 s typiques |

Avec des retries Meta, une API lente ou beaucoup d’insights, `batch:run` peut atteindre 10–15 min. Ces valeurs sont des estimations à confirmer avec `batch_run` sur la production.

## Scheduling proposé

Utiliser un JAR, une configuration et une base distincts par webTV. Éviter deux exécutions simultanées pour le même tenant.

### WebTV A — diffusion vendredi à 11 h

```cron
# Suivi de la publication du vendredi
10 11 * * 5  java -jar /opt/ugo2-a/batch.jar batch:run
0 12,15,21 * * 5  java -jar /opt/ugo2-a/batch.jar batch:run
0 9 * * 6  java -jar /opt/ugo2-a/batch.jar batch:run

# Entretien quotidien
0 6 * * 0-4  java -jar /opt/ugo2-a/batch.jar batch:run

# Publication rare du mardi : activer uniquement les semaines concernées
# 10 11 * * 2  java -jar /opt/ugo2-a/batch.jar batch:run
# 0 12,15 * * 2  java -jar /opt/ugo2-a/batch.jar batch:run
```

### WebTV B — diffusion jeudi à 17 h

```cron
10 17 * * 4  java -jar /opt/ugo2-b/batch.jar batch:run
0 18,21 * * 4  java -jar /opt/ugo2-b/batch.jar batch:run
0 9 * * 5  java -jar /opt/ugo2-b/batch.jar batch:run
0 6 * * 0-3,6  java -jar /opt/ugo2-b/batch.jar batch:run
```

## Santé et tokens

La page **Santé** affiche :

- dernier succès du collecteur et âge du dernier snapshot par plateforme ;
- durée et nombre d’éléments du dernier passage ;
- dernier batch et dernier refresh ;
- erreurs de collecte ;
- token expiré, bientôt expiré ou probablement refusé.

Quand la date est connue :

```properties
ugo2.facebook.token-expires-at=2026-12-31T23:59:59Z
ugo2.instagram.token-expires-at=2026-12-31T23:59:59Z
```

Une clé YouTube classique n’a généralement pas de date d’expiration connue ; laisser la propriété vide.

## Archive de revue

```bash
./createArchive.sh
```

L’archive contient `files.txt`, les sources, tests, SQL et documentation. Elle exclut les builds, médias, IDE et configurations réelles.
