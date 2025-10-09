
Ingestion METRICS
-----------------

### `POST /api/v1/metrics:batchUpsert`

**Description**  
Importe ou met à jour des *snapshots* de métriques dans la table `metric_snapshot`.  
Chaque enregistrement est lié à une vidéo source via `(platform, platform_video_id)`.

---

### 🔒 Authentification
Le contrôleur vérifie une authentification d’ingestion :
- `requireIngestAuth()` ou `requireValidRequest()` selon la config du backend.  
  → Le jeton doit être valide avant l’insertion.

---

### 📥 Entrée

**Structure attendue :**
```json
{
  "snapshots": [
    {
      "platform": "YOUTUBE",
      "platform_video_id": "abc123",
      "snapshot_at": "2025-10-09T12:00:00Z",

      "views_native": 10500,
      "likes": 340,
      "comments": 25,
      "shares": 12,

      "avg_watch_seconds": 37.5,
      "total_watch_seconds": 392000,
      "video_length_seconds": 45,

      "reach": 8700,
      "unique_viewers": 8200,

      "reactions_total": 500,
      "reactions_like": 400,
      "reactions_love": 80,
      "reactions_wow": 10,
      "reactions_haha": 5,
      "reactions_sad": 3,
      "reactions_angry": 2,

      "legacy_views_3s": 9500
    }
  ]
}
```

### Normalisation appliquée côté serveur

| Champ d’entrée                                                           | Traitement côté API                                                                 |
| ------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| `platform`                                                               | Uppercase, défaut = `"YOUTUBE"`                                                     |
| `platform_video_id`                                                      | Obligatoire ; chaîne non vide sinon ignoré (`skipped`)                              |
| `snapshot_at`                                                            | Converti en ISO UTC (`toIsoUtc`), accepte ISO, epoch s/ms, ou `YYYY-mm-dd HH:ii:ss` |
| `views_native`, `likes`, `comments`, `shares`, `reach`, `unique_viewers` | Convertis en `int` ou `null`                                                        |
| `avg_watch_seconds`, `total_watch_seconds`                               | Convertis en `float` ou `null`                                                      |
| `video_length_seconds`                                                   | Converti en `int` ou `null`                                                         |
| `reactions_*`                                                            | Convertis en `int` ou `null`                                                        |
| `legacy_views_3s` / `views_3s`                                           | Fusionnés dans `legacy_views_3s`                                                    |
| Autres champs inconnus                                                   | Ignorés                                                                             |

### Réponse
```json
{
  "status": "ok",
  "ok": 195,
  "ko": 3,
  "items": [
    { "platform": "YOUTUBE", "platform_video_id": "abc123", "status": "upserted" },
    { "platform": "FACEBOOK", "platform_video_id": "987654321", "status": "skipped" }
  ],
  "skipped_pre": 2
}
```

### Notes techniques
- Chaque snapshot est rattaché à une source existante via (platform, platform_video_id) ;
- si aucune correspondance, l’entrée est rejetée côté service.
- L’API supporte les timestamps ISO, Unix secondes et millisecondes.
- Toutes les dates sont converties en UTC avant insertion.
- Le service interne MetricsIngestService::ingestBatch() réalise les opérations d’upsert sur metric_snapshot
  avec clé unique (source_video_id, snapshot_at).
