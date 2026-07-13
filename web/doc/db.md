# Structure de la base UGO2

Une base MariaDB distincte est utilisée pour chaque webTV. Toutes les dates métier sont stockées en UTC.

## Tables métier

| Table | Rôle |
|---|---|
| `video` | Vidéo canonique. Plusieurs publications multi-plateformes peuvent y être rattachées. |
| `source_video` | Publication identifiée par `(platform, platform_video_id)`. |
| `metric_snapshot` | Mesure horodatée d’une source : vues, audience, engagement et temps de visionnage. |
| `reconcile_override` | Correction manuelle de la réconciliation : lien ou séparation. |

```text
video 1 ---- n source_video 1 ---- n metric_snapshot
```

## Vues et tables analytiques

| Objet | Utilisation |
|---|---|
| `v_metric_snapshot_enriched` | Snapshot enrichi avec plateforme, vidéo canonique et ratios calculés. |
| `v_source_latest_snapshot` | Dernier snapshot de chaque source. |
| `v_source_latest_enriched` | Dernier snapshot enrichi de chaque source. |
| `v_video_last_snapshot` | Date de dernière mesure par vidéo canonique. |
| `mv_video_rollup` | Dernières métriques agrégées par vidéo et plateforme pour la liste et les tris. |
| `mv_video_views_aligned_*` | Séries alignées sur la publication pour les graphes comparatifs. |
| `mv_video_views_percentiles` | Bandes de référence calculées sur plusieurs vidéos. |
| `mv_refresh_control` | Contrôle historique du refresh analytique. |
| `refresh_job_state` | État sale/propre, verrou logique, durée et dernière erreur du refresh. |
| `platform_health` | Dernier succès, erreur, durée, fraîcheur et état du token par plateforme. |
| `batch_run` | Historique minimal des exécutions du batch. |

## Règles d’intégrité

- `(platform, platform_video_id)` est unique dans `source_video`.
- `(source_video_id, snapshot_at)` est unique dans `metric_snapshot`.
- `views_native` et `total_watch_seconds` ne diminuent pas pour une même source.
- `NULL` signifie « non reçu/inconnu » ; zéro doit représenter une vraie mesure.
- Un snapshot est conservé au premier point, pour un delta significatif, un changement utile ou le garde-fou quotidien.
- Les ingestions marquent les vues comme sales ; `batch:run` déclenche un refresh unique en fin d’exécution.

## Scripts SQL

```text
001_schema.sql                    schéma initial — destructif, base vide uniquement
002_views.sql                     vues analytiques initiales
003_indexes.sql                   index complémentaires
004_reconcile.sql                 corrections manuelles
005_views_fallback_on_reach.sql   vues avec fallback reach
006_performances.sql              rollup et contrôle historique de refresh
007_graph_views.sql               structures des graphes
008_health_sparse_refresh.sql     santé, batches et refresh différé
```

Les scripts historiques ne constituent pas encore une chaîne de migration sûre. Le remplacement par des migrations versionnées reste un MUST.
