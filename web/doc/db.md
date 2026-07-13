# Structure de la base UGO2

Une base MariaDB distincte est utilisée pour chaque webTV. Toutes les dates métier sont stockées en UTC.

## Tables métier

| Table | Rôle |
|---|---|
| `video` | Vidéo canonique. Plusieurs publications multi-plateformes peuvent y être rattachées. |
| `source_video` | Publication identifiée par `(platform, platform_video_id)`. Elle peut être liée à une `video`. |
| `metric_snapshot` | Mesure horodatée d’une source : vues, audience, engagement et temps de visionnage. |
| `reconcile_override` | Correction manuelle de la réconciliation : lien, séparation ou exclusion. |

Relations principales :

```text
video 1 ---- n source_video 1 ---- n metric_snapshot
  |                    |
  +---- n reconcile_override n ----+
```

## Vues et tables analytiques

| Objet | Utilisation |
|---|---|
| `v_metric_snapshot_enriched` | Snapshot enrichi avec plateforme, vidéo canonique et ratios calculés. |
| `v_source_latest_snapshot` | Dernier snapshot de chaque `source_video`. |
| `v_source_latest_enriched` | Dernier snapshot enrichi de chaque source. |
| `v_video_last_snapshot` | Date de dernière mesure par vidéo canonique. |
| `mv_video_rollup` | Dernières métriques agrégées par vidéo et plateforme pour la liste. |
| `mv_video_views_aligned_*` | Séries alignées sur la publication pour les graphes comparatifs. |
| `mv_video_views_percentiles` | Bandes de référence calculées sur plusieurs vidéos. |
| `mv_refresh_control` | Contrôle du dernier refresh analytique. |

## Règles d’intégrité attendues

- `(platform, platform_video_id)` est unique dans `source_video`.
- `(source_video_id, snapshot_at)` est unique dans `metric_snapshot`.
- Les vues cumulées ne diminuent pas pour une même source.
- `null` signifie « non reçu/inconnu » ; zéro doit représenter une vraie mesure.
- Les snapshots identiques ou quasi identiques ne doivent pas être conservés inutilement.

## Scripts SQL actuels

```text
001_schema.sql               schéma initial — destructif, réservé à une base vide
002_views.sql                vues analytiques initiales
003_indexes.sql              index complémentaires
004_reconcile.sql            table des corrections manuelles
005_views_fallback_on_reach.sql vues avec fallback reach
006_performances.sql         rollup et contrôle de refresh
007_graph_views.sql          structures historiques pour graphes
```

Ces scripts reflètent plusieurs étapes d’évolution et ne constituent pas encore une chaîne de migration sûre. Avant la prochaine mise en production, les convertir en migrations versionnées et aligner les structures de graphes avec `MaterializedViewsSql.php`.

Les diagrammes PlantUML sont disponibles dans `db-simple.puml` et `db.puml`.
