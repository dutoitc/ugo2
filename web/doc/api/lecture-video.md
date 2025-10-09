
## GET /api/v1/video

Récupère le détail d’une vidéo canonique, ses sources liées, et éventuellement les séries temporelles.

### Sélection de la vidéo (obligatoire — **une** des options)
- `id` : identifiant de la vidéo canonique
- `slug` : slug exact
- `(platform, platform_video_id)` : couple plateforme + ID source (résout la vidéo liée)

### Options
- `timeseries=1` : inclut `timeseries` (snapshots ordonnés `DESC`) pour chaque source
- `ts_limit` : nombre max de points par source (défaut **50**, max **1000**)

### Réponse (200)
```json
{
  "video": {
    "id": 42,
    "slug": "mon-slug",
    "title": "Titre de la vidéo",
    "description": "Texte…",
    "published_at": "2025-09-25 14:22:10",
    "duration_seconds": 95,
    "is_locked": false
  },
  "rollup": {
    "video_id": 42,
    "slug": "mon-slug",
    "video_title": "Titre de la vidéo",
    "video_published_at": "2025-09-25 14:22:10",
    "canonical_length_seconds": 95,
    "views_native_sum": 15000,
    "likes_sum": 320,
    "comments_sum": 45,
    "shares_sum": 12,
    "total_watch_seconds_sum": 405000,
    "views_yt": 12000,
    "views_fb": 2000,
    "views_ig": 800,
    "views_tt": 200,
    "avg_watch_ratio_est": 0.42,
    "watch_equivalent_sum": 4263.0,
    "engagement_rate_sum": 0.025
  },
  "sources": [
    {
      "id": 1001,
      "platform": "YOUTUBE",
      "platform_format": "VIDEO",
      "platform_video_id": "abc123",
      "title": "Titre source",
      "url": "https://youtube.com/watch?v=abc123",
      "published_at": "2025-09-25 14:22:10",
      "duration_seconds": 95,
      "is_active": true,
      "latest": {
        "...": "ligne de v_source_latest_enriched"
      },
      "timeseries": null
    }
  ]
}
```

### Avec séries temporelles
Appeler par ex. :  
`GET /api/v1/video?id=42&timeseries=1&ts_limit=200`

Dans chaque `source.timeseries`, on récupère des lignes issues de `v_metric_snapshot_enriched` (ordonnées par `snapshot_at DESC`, limitées à `ts_limit`).

### Codes d’erreur
- `200 OK` : succès
- `400 Bad Request` : fournir `id` **ou** `slug` **ou** `(platform, platform_video_id)`
- `404 Not Found` : vidéo introuvable
- `500 Internal Server Error` : erreur inattendue

### Notes
- `latest` est une ligne de `v_source_latest_enriched` (dernière métrique par source).
- `timeseries` est une liste issue de `v_metric_snapshot_enriched` (si demandé).
- Les vues et ratios sont **les derniers états** au moment du dernier snapshot par source.
