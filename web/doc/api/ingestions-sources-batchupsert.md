
___
### `POST /api/v1/sources:batchUpsert`
#### Description :
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

#### Entrée exemple:
```json
{
  "sources": [
    {
      "platform": "YOUTUBE",
      "platform_video_id": "abc123",
      "platform_format": "VIDEO",
      "platform_channel_id": "UCxxxx",
      "title": "Titre",
      "description": "Texte…",
      "url": "https://youtube.com/watch?v=abc123",
      "etag": "etag-xyz",
      "published_at": "2025-10-09T12:00:00Z",
      "duration_seconds": 120
    }
  ]
}
```
Alias acceptés — exemples équivalents
```json
{ "sources": [ { "platform":"FACEBOOK", "platformId":"123_456", "media_type":"REEL", "permalink_url":"https://fb…"} ] }
{ "sources": [ { "platform":"INSTAGRAM", "id":"IGVID_789", "publishedAt":"2025-10-09T12:00:00Z" } ] }
```
Réponse
```json
{ "ok": true, "inserted": 10, "updated": 5, "skipped": 2 }
```