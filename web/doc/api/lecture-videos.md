# `GET /api/v1/videos`

Liste paginée des vidéos canoniques avec leurs derniers agrégats.

## Paramètres

- `page` : entier ≥ 1, défaut `1` ;
- `size` : entier de 1 à 5000, défaut `20` ;
- `q` : recherche sur le titre et le slug ;
- `platform` : `YOUTUBE`, `FACEBOOK`, `INSTAGRAM` ou `TIKTOK` ;
- `format` : `VIDEO`, `SHORT` ou `REEL` ;
- `from`, `to` : bornes ISO sur la date de publication ;
- `sort` :
  - `published_desc`, `published_asc` ;
  - `views_desc`, `views_asc` ;
  - `youtube_desc`, `youtube_asc` ;
  - `facebook_desc`, `facebook_asc` ;
  - `instagram_desc`, `instagram_asc` ;
  - `tiktok_desc`, `tiktok_asc` ;
  - `engagement_desc`, `engagement_asc` ;
  - `watch_eq_desc`, `watch_eq_asc` ;
  - `title_asc`, `title_desc`.

## Réponse 200

```json
{
  "page": 1,
  "size": 20,
  "total": 1234,
  "sum": {
    "youtube": 120000,
    "facebook": 45000,
    "instagram": 18000,
    "tiktok": 0
  },
  "items": [
    {
      "id": 42,
      "slug": "mon-slug",
      "title": "Titre de la vidéo",
      "published_at": "2026-07-10 09:00:00",
      "views_native_sum": 17800,
      "likes_sum": 320,
      "comments_sum": 45,
      "shares_sum": 12,
      "engagement_rate_sum": 0.025,
      "by_platform": {
        "YOUTUBE": 12000,
        "FACEBOOK": 5000,
        "INSTAGRAM": 800,
        "TIKTOK": 0
      },
      "last_snapshot_at": "2026-07-13 10:02:33"
    }
  ]
}
```

Les tris sont exécutés en SQL sur `mv_video_rollup`, avant pagination. Les valeurs `NULL` sont assimilées à zéro pour les tris par plateforme, avec la date et l’ID comme critères de départage stables.
