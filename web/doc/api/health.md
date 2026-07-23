# Santé

## `GET /api/v1/health`

Retourne l’état affiché par la page `/health` : fraîcheur par plateforme, dernier batch, dernier refresh et alertes.

```json
{
  "ok": true,
  "service": "ugo2-api",
  "now_utc": "2026-07-13T17:30:00+00:00",
  "alerts": ["Token FACEBOOK bientôt expiré."],
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

Le statut global d’une plateforme est `OK`, `WARNING` ou `ERROR`. `token_status` utilise `OK`, `WARNING`, `EXPIRED`, `ERROR` ou `UNKNOWN`. Les messages enregistrés et renvoyés passent par le masque de données sensibles.

Si le calcul de santé échoue, l’API renvoie `503` avec `{"ok":false,"service":"ugo2-api","error":"health_unavailable"}` sans détail interne.

## `POST /api/v1/health:report`

Le batch envoie soit :

- un événement `type=platform` avec `platform`, `status`, dates, durée, nombre d’éléments et message facultatif ;
- un événement `type=batch` avec `run_id`, dates, durée, statut, nombre d’éléments et erreur facultative.

Un token ou une clé ne doit jamais être placé dans le corps. Une date `token_expires_at` peut être transmise. L’authentification HMAC n’étant pas encore imposée par le contrôleur actuel, cette route doit rester interne.
