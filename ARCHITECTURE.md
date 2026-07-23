# Architecture UGO2

## Vue d’ensemble

```text
YouTube / Facebook / Instagram
               |
               v
       batch/ — Java 21
  découverte, collecte, normalisation
               |
               | JSON sur /api/v1/*
               v
    web/src/back/ — PHP 8 + PDO
 ingestion, réconciliation, lectures, santé
               |
               v
             MariaDB
               ^
               |
        web/ihm/ — Angular 20
 listes, détail, graphes, doublons, santé
```

## Responsabilités

| Module | Responsabilité | Ne fait pas |
|---|---|---|
| `batch/` | Appeler les APIs externes, mapper les réponses, envoyer des lots et rapports de santé. | Accéder directement à MariaDB. |
| `web/src/back/` | Router les appels, valider les données, persister, réconcilier et produire les lectures. | Collecter directement les plateformes. |
| `web/ihm/` | Consommer les lectures JSON et présenter les opérations disponibles. | Calculer ou persister les données métier. |
| `web/sql/` | Décrire le schéma et les objets analytiques historiques. | Fournir aujourd’hui une chaîne de migration sûre. |

## Flux d’un batch

1. Le batch découvre ou met à jour les `source_video`.
2. Il envoie les `metric_snapshot` par lots.
3. L’API préserve les valeurs inconnues, corrige les régressions de compteurs cumulés et ne conserve que les points utiles.
4. Le batch lance la réconciliation puis `POST /api/v1/refresh:run`.
5. Le refresh met à jour `mv_video_rollup` et les tables de séries temporelles sous verrou MariaDB.
6. Le batch rapporte la santé des plateformes et de l’exécution.

## Modèle de données

`video` est l’entité canonique. Une `video` possède zéro à plusieurs `source_video`, chacune identifiée de façon unique par `(platform, platform_video_id)`. Une source possède des `metric_snapshot` horodatés en UTC.

Les vues enrichies fournissent le dernier snapshot. `mv_video_rollup` sert la liste et ses tris ; les tables `mv_video_views_*` servent les courbes et bandes de percentiles. Les tables `platform_health`, `batch_run` et `refresh_job_state` portent l’état d’exploitation.

## Frontend

Angular expose quatre routes : `/`, `/v/:id`, `/duplicates` et `/health`. Le détail charge séparément les métadonnées et les séries temporelles. ECharts est empaqueté par Angular ; aucun CDN n’est utilisé.

## Limites de sécurité actuelles

- Les valeurs sensibles sont masquées dans les logs Java/PHP, les erreurs JSON et les états de santé.
- Les scans Gitleaks couvrent l’historique Git et l’archive générée.
- Le client Java sait signer les requêtes HMAC et `Auth.php` sait les vérifier.
- Cette vérification n’est pas encore raccordée systématiquement aux contrôleurs. Toutes les mutations, y compris la résolution de doublons, sont à considérer comme internes jusqu’à sa mise en place.

Les configurations réelles restent hors Git. Une base et des configurations distinctes assurent l’isolation entre instances ; il n’existe pas de `tenant_id` partagé.
