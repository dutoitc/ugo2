# Ingestion des sources

## `POST /api/v1/sources:batchUpsert`

Upsert de sources dans source_video. Contrainte d’unicité: (platform, platform_video_id).
Chaque item tolère plusieurs alias d’entrée et est inséré ou mis à jour.

### Normalisations & alias (côté serveur)
- platform: valeur par défaut YOUTUBE, uppercased.
- platform_video_id: pris depuis le premier présent parmi
- platform_video_id → platformId → platform_source_id → id (obligatoire au final).
- platform_format: platform_format ou media_type; valeurs permises: VIDEO|SHORT|REEL (fallback VIDEO).
- platform_channel_id: optionnel.
- url: url ou permalink_url.
- published_at: published_at ou publishedAt (ISO-8601 → DATETIME en UTC, secondes, sans millisecondes).
- duration_seconds: entier optionnel.
- is_active: fixé à 1 lors de l’insert; reste à 1 lors de l’update via l’upsert (cf. SQL).

### Exemple

```json
{
  "sources": [
    {
      "platform": "YOUTUBE",
      "platform_video_id": "video-example-1",
      "platform_format": "VIDEO",
      "platform_channel_id": "channel-example-1",
      "title": "Vidéo exemple",
      "description": "Description neutre",
      "url": "https://media.example.org/videos/video-example-1",
      "etag": "etag-example-1",
      "published_at": "2026-07-13T12:00:00Z",
      "duration_seconds": 120
    }
  ]
}
```

### Réponse

```json
{ "ok": true, "inserted": 10, "updated": 5, "skipped": 2 }
```

L’authentification HMAC n’est pas encore imposée côté serveur ; cette route doit rester interne.
