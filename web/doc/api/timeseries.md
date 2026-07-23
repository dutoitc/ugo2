# Séries temporelles

## `GET /api/v1/video/{id}/timeseries`

Retourne une série globale et une série par plateforme pour une vidéo canonique.

## Paramètres

| Paramètre | Valeurs | Défaut |
|---|---|---|
| `metric` | `views_native`, `likes`, `comments`, `shares`, `total_watch_seconds` | `views_native` |
| `interval` | `hour`, `day` | `hour` |
| `range` | `all`, `full`, ou durée comme `24h`, `7d`, `180d` | `all` |
| `platforms` | CSV de plateformes | toutes |
| `agg` | `sum`, `cumsum` | `sum` |
| `limit` | nombre maximal de points après sous-échantillonnage | `0` (désactivé) |
| `include` | contient `percentiles` pour les bandes de référence | vide |

Pour `all`/`full`, la fenêtre commence à la publication si elle est connue, sinon sept jours avant l’appel. Chaque bucket conserve le maximum de la métrique par plateforme ; la série globale additionne ensuite les plateformes.

```http
GET /api/v1/video/42/timeseries?metric=views_native&interval=hour&range=7d&include=percentiles
```

```json
{
  "timeseries": {
    "views": [{"ts": "2026-07-13 17:00:00.000", "value": 1510}],
    "YOUTUBE": [{"ts": "2026-07-13 17:00:00.000", "value": 1000}],
    "FACEBOOK": [{"ts": "2026-07-13 17:00:00.000", "value": 510}]
  },
  "granularity": "hour",
  "metric": "views_native",
  "from": "2026-07-06 17:30:00.000",
  "to": "2026-07-13 17:30:00.000",
  "message": "range=7d | interval=hour | metric=views_native | ..."
}
```

Lorsque `include=percentiles` est demandé pour `views_native`, la réponse peut aussi contenir `percentiles` par plateforme. L’endpoint ne possède pas de variante `/video/timeseries?id=...` dans le routeur actuel.
