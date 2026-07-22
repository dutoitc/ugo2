# API PHP UGO2

Préfixe : `/api/v1`. Les réponses sont en JSON et les dates métier sont stockées en UTC.

## État de l’authentification

Les lectures `GET` sont publiques dans le routeur actuel. Le client Java sait signer les écritures et `Auth.php` contient le validateur HMAC, mais les contrôleurs ne l’imposent pas encore. Jusqu’à correction du MUST correspondant, toutes les routes `POST` doivent rester derrière un réseau ou proxy de confiance.

## Routes de lecture

| Méthode et route | Fonction | Documentation |
|---|---|---|
| `GET /api/v1/health` | Santé des collecteurs, batch et refresh. | [Santé](api/health.md) |
| `GET /api/v1/videos` | Liste paginée et triée. | [Liste](api/lecture-videos.md) |
| `GET /api/v1/video` | Détail par `id`, `slug` ou identifiant de plateforme. | [Détail](api/lecture-video.md) |
| `GET /api/v1/video/{id}/timeseries` | Séries et percentiles. | [Séries](api/timeseries.md) |
| `GET /api/v1/duplicates` | Paires de sources candidates au rapprochement. | Contrat utilisé par l’IHM. |

## Routes batch et administration

| Méthode et route | Corps principal | Effet |
|---|---|---|
| `POST /api/v1/sources:filterMissing` | `platform` + `ids`, ou `sources` | Lecture interne des IDs absents. |
| `POST /api/v1/sources:batchUpsert` | `sources[]` | Insère ou met à jour des sources. |
| `POST /api/v1/metrics:batchUpsert` | `snapshots[]` | Stocke les snapshots utiles. |
| `POST /api/v1/reconcile:run` | `from`, `to`, `hoursWindow`, `dryRun` facultatifs | Rapproche les sources ; même `dryRun` applique actuellement les overrides en attente. |
| `POST /api/v1/overrides:apply` | Liste d’actions `LINK`/`UNLINK` | Crée des corrections de réconciliation. |
| `POST /api/v1/refresh:run` | Objet JSON vide | Marque les vues sales puis force leur refresh. |
| `POST /api/v1/health:report` | Événement `platform` ou `batch` | Met à jour l’état d’exploitation. |
| `POST /api/v1/duplicates:resolve` | IDs à conserver, supprimer et rattacher | Fusionne un doublon depuis l’IHM. |

Contrats détaillés :

- [Filtrer les sources absentes](api/ingestions-sources-filtermissing.md)
- [Ingestion des sources](api/ingestions-sources-batchupsert.md)
- [Ingestion des métriques](api/ingestions-metrics-batchupsert.md)

Les anciennes mutations `GET` de refresh et les routes d’agrégats qui répondaient `501` ont été supprimées. Une route absente renvoie `404`; une mauvaise méthode sur une route exacte renvoie `405` avec l’en-tête `Allow`.
