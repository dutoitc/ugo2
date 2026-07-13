# Santé

## `GET /api/v1/health`

Retourne l’état d’exploitation affiché par la page **Santé** : fraîcheur par plateforme, dernier batch, dernier refresh et alertes de token.

```json
{
  "ok": true,
  "service": "ugo2-api",
  "now_utc": "2026-07-13T17:30:00+00:00",
  "alerts": [
    "Token FACEBOOK expire le 2026-07-20 12:00:00.000."
  ],
  "platforms": [
    {
      "platform": "FACEBOOK",
      "status": "WARNING",
      "token_status": "WARNING",
      "token_expires_at": "2026-07-20 12:00:00.000",
      "last_success_at": "2026-07-13 17:20:00.000",
      "last_snapshot_at": "2026-07-13 17:19:55.000",
      "snapshot_age_hours": 0.2,
      "last_duration_ms": 42110,
      "last_items": 200,
      "last_error": null,
      "source_count": 380
    }
  ],
  "last_batch": {
    "status": "SUCCESS",
    "duration_ms": 122400,
    "items": 580
  },
  "refresh": {
    "last_status": "SUCCESS",
    "last_duration_ms": 8200,
    "last_success_at": "2026-07-13 17:22:10.000"
  }
}
```

Les statuts de plateforme sont `OK`, `WARNING` ou `ERROR`. Le token peut être `OK`, `WARNING`, `EXPIRED`, `PROBABLY_EXPIRED`, `ERROR` ou `UNKNOWN`.

## `POST /api/v1/health:report`

Endpoint signé utilisé par le batch pour rapporter :

- le succès ou l’échec de chaque plateforme ;
- la durée et le nombre de métriques collectées ;
- une erreur compatible avec un token expiré ;
- le démarrage et la fin du batch.

Il n’accepte aucun token dans le corps : seule une date d’expiration facultative peut être transmise.
