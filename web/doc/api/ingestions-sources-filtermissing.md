
# Filtrer les sources absentes

## `POST /api/v1/sources:filterMissing`

Vérifie quelles sources sont absentes de la base avant un import massif.

### Entrées tolérées

```json
{ "platform": "YOUTUBE", "ids": ["video-example-1", "video-example-2"] }
```

Les clés `platformIds` ou `sources[].platformId` sont aussi acceptées.

### Réponse

```json
{
  "platform": "YOUTUBE",
  "requestedCount": 2,
  "existingCount": 1,
  "missingCount": 1,
  "missing": ["video-example-2"]
}
```

Cette route fait une lecture mais reste un appel interne `POST`. L’authentification HMAC n’est pas encore imposée côté serveur.
