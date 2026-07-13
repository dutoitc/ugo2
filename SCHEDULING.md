# Collecte, quotas et stockage

Ce document décrit la **cible**. Une partie de cette politique n’est pas encore appliquée par le code actuel ; les écarts sont listés dans `TODO.md`.

## Étapes d’un run

```text
discovery -> collecte métriques -> ingestion API -> réconciliation -> refresh analytique
```

Le refresh analytique doit être effectué une seule fois après l’ingestion, pas après chaque sous-lot.

## Cadence cible

| Âge de la vidéo | Collecte métriques |
|---|---|
| Jour de publication | Fréquente, selon les créneaux éditoriaux de la webTV |
| J+1 à J+7 | Toutes les 3 à 6 heures |
| J+8 à J+30 | Une fois par jour |
| Plus d’un mois | Une fois par semaine, sauf vidéo encore active |

La discovery peut être moins fréquente que la collecte des métriques. Les vidéos anciennes ne doivent pas toutes être relues à chaque run.

## Quotas et reprise

- Batchs maximaux adaptés à chaque API.
- ETag/`If-None-Match` lorsque la plateforme le permet.
- Backoff exponentiel avec jitter sur limitation ou erreur temporaire.
- Checkpoint par plateforme et webTV pour reprendre un run interrompu.
- Une erreur Facebook ne bloque pas YouTube ou Instagram.

## Parcimonie des snapshots

Insérer un snapshot si au moins une condition est vraie :

- première mesure de la source ;
- variation absolue de vues au-dessus du seuil ;
- variation relative au-dessus du seuil ;
- autre métrique utile modifiée ;
- aucun point de garde enregistré pour la journée.

Ne pas insérer si toutes les métriques utiles sont identiques. Un zéro ou une baisse de vues après une valeur positive est rejeté/journalisé selon la règle de monotonie.

## Maintenance

- Diagnostic quotidien léger : données trop anciennes, zéros temporaires, régressions, sources sans métriques.
- Assainissement périodique en `dry-run`, puis correction explicitement déclenchée.
- Refresh des percentiles moins fréquent que le rollup principal.
