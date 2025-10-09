
## GET /api/v1/videos

Liste paginée des vidéos canoniques (avec agrégats récents).

### Query params
- `page` : entier ≥ 1 (défaut **1**)
- `size` : entier entre 1 et 5000 (défaut **20**)
- `q` : texte de recherche (LIKE sur `video_title` et `slug`)
- `platform` : `YOUTUBE` | `FACEBOOK` | `INSTAGRAM` | `TIKTOK` (filtre : vidéos ayant des vues > 0 sur la plateforme)
- `format` : `VIDEO` | `SHORT` | `REEL` (filtre via existence d’au moins une `source_video` avec ce format)
- `from`, `to` : bornes de date (ISO) sur `video_published_at` (inclusif / exclusif)
- `sort` :
    - `views_desc` (défaut) → `views_native_sum DESC, video_published_at DESC`
    - `published_desc` → `video_published_at DESC, video_id DESC`
    - `published_asc`  → `video_published_at ASC,  video_id ASC`
    - `engagement_desc` → `engagement_rate_sum DESC (NULLS LAST), views_native_sum DESC`
    - `watch_eq_desc` → `watch_equivalent_sum DESC (NULLS LAST), views_native_sum DESC`
    - `title_asc`  → `video_title ASC,  video_id DESC`
    - `title_desc` → `video_title DESC, video_id DESC`

### Réponse (200)
```json
{
  "page": 1,
  "size": 20,
  "total": 1234,
  "items": [
    {
      "id": 42,
      "slug": "mon-slug",
      "title": "Titre de la vidéo",
      "published_at": "2025-09-25 14:22:10",
      "length_seconds": 95,

      "views_native_sum": 15000,
      "likes_sum": 320,
      "comments_sum": 45,
      "shares_sum": 12,

      "total_watch_seconds_sum": 405000,
      "avg_watch_ratio_est": 0.42,
      "watch_equivalent_sum": 4263.0,
      "engagement_rate_sum": 0.025,

      "by_platform": {
        "YOUTUBE": 12000,
        "FACEBOOK": 2000,
        "INSTAGRAM": 800,
        "TIKTOK": 200
      },

      "last_snapshot_at": "2025-09-26 10:02:33"
    }
  ]
}
```

### Codes d’erreur
- `200 OK` : succès
- `400 Bad Request` : paramètres invalides
- `500 Internal Server Error` : erreur inattendue

### Notes
- Les données proviennent de la vue `v_video_latest_rollup` (agrégats par vidéo au **dernier snapshot** de chaque source).
- Le champ `last_snapshot_at` est calculé via sous-requête sur `metric_snapshot.created_at` (max par vidéo).
