# Ingestion des métriques

## `POST /api/v1/metrics:batchUpsert`

Importe un lot de métriques et rattache chaque point à une source par `(platform, platform_video_id)`.

```json
{
  "snapshots": [
    {
      "platform": "YOUTUBE",
      "platform_format": "VIDEO",
      "platform_video_id": "abc123",
      "snapshot_at": "2026-07-13T12:00:00Z",
      "views_native": 10500,
      "likes": 340,
      "comments": 25,
      "shares": 12,
      "avg_watch_seconds": 37,
      "total_watch_seconds": 392000,
      "video_length_seconds": 45,
      "reach": null
    }
  ]
}
```

## Règles d’intégrité

- `null` signifie « inconnue » et ne remplace pas la dernière valeur connue ;
- `views_native` et `total_watch_seconds` ne peuvent pas régresser ;
- un point hors ordre chronologique est ignoré ;
- un snapshot est stocké seulement si le delta de vues dépasse le seuil absolu ou relatif, si une métrique utile change, ou pour le point de garde quotidien ;
- si aucun point n’est stocké, aucun refresh analytique n’est demandé ;
- le refresh réel est exécuté une seule fois en fin de batch par `POST /api/v1/refresh:run`.

## Réponse 200

```json
{
  "status": "ok",
  "ok": 195,
  "ko": 0,
  "stored": 24,
  "skipped": 171,
  "monotonic_corrections": 2,
  "refresh_requested": true,
  "items": [
    {
      "i": 0,
      "status": "stored",
      "reason": "views_delta",
      "source_video_id": 42,
      "views_delta": 25,
      "monotonic_corrections": 0
    }
  ]
}
```

Raisons possibles : `first_snapshot`, `same_timestamp_update`, `views_delta`, `useful_metric_changed`, `daily_guard`, `below_threshold` et `out_of_order`.
