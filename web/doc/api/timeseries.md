# API UGO2 — Timeseries (READ)
Format JSON (`Content-Type: application/json`). Toutes les dates en UTC.

---

## GET /api/v1/video/{id}/timeseries

Séries temporelles agrégées pour une vidéo canonique, par métrique et par granularité.

> **Alternative supportée** : `/api/v1/video/timeseries?id={id}` (le contrôleur tente d’extraire `id` depuis le chemin si absent des query params).

### Query params
- `metric` : `views_native` \| `likes` \| `comments` \| `shares` \| `total_watch_seconds` (**défaut:** `views_native`)
- `interval` : `hour` \| `day` (**défaut:** `hour`)
- `range` : fenêtre relative, ex. `24h`, `7d`, `30d` (**défaut:** `7d`)
- `platforms` : CSV, ex. `FACEBOOK,YOUTUBE` (**défaut:** *toutes*)
- `agg` : `sum` \| `cumsum` (**défaut:** `sum`) — `cumsum` renvoie la somme cumulée **dans le temps**
- `limit` : entier `> 0` pour downsampling uniforme (**défaut:** `0` = désactivé)

### Sémantique & calcul
1. **Bucketisation (UTC)**
    - `hour` → bucket au format `YYYY-MM-DD HH:00:00.000`
    - `day`  → bucket au format `YYYY-MM-DD 00:00:00.000`

2. **Agrégat par plateforme (intra-bucket)**  
   Pour chaque plateforme, on prend **MAX(metric)** de tous les snapshots du bucket.

3. **Série globale**  
   Pour chaque bucket, on **somme** les valeurs MAX de toutes les plateformes.

4. **Cumul optionnel** (`agg=cumsum`)  
   Appliqué à la série globale **et** aux séries par plateforme (croissance dans le temps).

5. **Downsampling optionnel** (`limit>0`)  
   Sélection uniforme d’environ `limit` points (conserve toujours le dernier point).

6. **Filtre plateformes** (`platforms`)  
   Restreint le calcul aux plateformes listées (CSV).

### Authentification
Optionnelle selon configuration : le contrôleur prévoit un hook (`assertApiKeyRead`) à activer si besoin.

---

## Exemples d’appel

### 1) Vues horaires sur 24h (global + par plateforme)
```
GET /api/v1/video/42/timeseries?metric=views_native&interval=hour&range=24h
```

### 2) Likes quotidiens cumulés, Instagram uniquement, downsample à 200 points
```
GET /api/v1/video/42/timeseries?metric=likes&interval=day&range=30d&platforms=INSTAGRAM&agg=cumsum&limit=200
```

### 3) Watch time total, 7 jours, YouTube+Facebook
```
GET /api/v1/video/42/timeseries?metric=total_watch_seconds&interval=day&range=7d&platforms=YOUTUBE,FACEBOOK
```

---

## Réponse (200)

```json
{
  "timeseries": {
    "views": [
      { "ts": "2025-10-08 18:00:00.000", "value": 1234 },
      { "ts": "2025-10-08 19:00:00.000", "value": 1311 }
    ],
    "YOUTUBE": [
      { "ts": "2025-10-08 18:00:00.000", "value": 1000 },
      { "ts": "2025-10-08 19:00:00.000", "value": 1080 }
    ],
    "FACEBOOK": [
      { "ts": "2025-10-08 18:00:00.000", "value": 200 },
      { "ts": "2025-10-08 19:00:00.000", "value": 210 }
    ]
  },
  "granularity": "hour",
  "metric": "views_native",
  "from": "2025-10-07 19:15:33.000",
  "to": "2025-10-08 19:15:33.000"
}
```

- `timeseries.views` : série **globale** (= somme des MAX par plateforme, bucket par bucket)
- `timeseries.<PLATFORM>` : série **par plateforme** (MAX intra-bucket)
- `granularity` : `hour` \| `day`
- `from` / `to` : bornes UTC (incluses côté affichage ; le SQL filtre `snapshot_at >= from`)

---

## Codes d’erreur
- `200 OK` — succès
- `400 Bad Request` — `id` manquant ou invalide
- `500 Internal Server Error` — erreur inattendue

---

## Notes techniques
- La mesure agrégée utilise `metric_snapshot.snapshot_at` (UTC) et `source_video.platform`/`video_id`.
- `platforms` est nettoyé/uppercased et dédoublonné côté API.
- Détection d’anomalies : le serveur loggue un **warning** si une série par plateforme décroit (non-monotonicité), signe d’un problème d’ingestion en amont.
- Le champ `metric` est whitelisté et mappé côté SQL (sécurité).

---

## cURL rapide

```bash
curl -s "https://api.ugo2.local/api/v1/video/42/timeseries?metric=views_native&interval=hour&range=24h"
```

```bash
curl -s "https://api.ugo2.local/api/v1/video/42/timeseries?metric=likes&interval=day&range=30d&platforms=INSTAGRAM&agg=cumsum&limit=200"
```
