# UGO2 — spécification de l’IHM implémentée

Ce document décrit l’interface Angular présente dans le dépôt. Les évolutions non implémentées restent uniquement dans [`TODO.md`](TODO.md) et ne sont pas présentées comme des routes disponibles.

## Routes

| Route Angular | Écran | Accès depuis la navigation |
|---|---|---|
| `/` | Liste des vidéos | Oui |
| `/v/:id` | Détail d’une vidéo canonique | Depuis la liste |
| `/duplicates` | Détection et résolution des doublons | URL directe |
| `/health` | Santé des collecteurs | Oui |
| autre | Redirection vers `/` | — |

## Liste des vidéos

La liste affiche 50 lignes par page. Elle présente la publication, le titre, les vues totales et par plateforme, le taux d’engagement et les étoiles de tendance.

Fonctions disponibles :

- recherche texte ;
- filtre par plateforme (`YOUTUBE`, `FACEBOOK`, `INSTAGRAM`, `TIKTOK`) ;
- tris publication, titre, vues globales/par plateforme, engagement et équivalent de visionnage ;
- pagination ;
- ouverture de `/v/:id` avec l’identifiant numérique canonique.

API utilisée : `GET /api/v1/videos` avec `page`, `size`, `q`, `platform` et `sort`. L’API accepte aussi `format`, `from` et `to`, mais l’IHM ne les expose pas encore.

## Détail vidéo

Le détail affiche :

- titre, description et date de publication ;
- vues, likes, commentaires, partages, durée de visionnage et taux d’engagement agrégés ;
- dernier snapshot de chaque plateforme et lien source ;
- courbe globale, courbes par plateforme et bandes de percentiles lorsqu’elles existent ;
- détail des réactions disponible dans les derniers snapshots.

APIs utilisées :

- `GET /api/v1/video?id=:id` ;
- `GET /api/v1/video/:id/timeseries?metric=views_native&interval=hour&include=percentiles`.

## Doublons

L’écran compare deux `source_video` proches et permet de choisir la vidéo canonique à conserver. L’action appelle `POST /api/v1/duplicates:resolve` après confirmation navigateur.

Cette mutation n’a pas encore d’authentification d’administration imposée côté PHP. L’écran ne doit pas être exposé publiquement avant la réalisation du MUST sécurité correspondant.

## Santé

L’écran appelle `GET /api/v1/health` et affiche, par plateforme, le statut, la fraîcheur du dernier snapshot et du dernier succès, la durée, l’état du token et un message assaini. Il montre aussi le dernier batch et le dernier refresh analytique.

## Règles d’affichage

- Les données sont stockées en UTC et affichées avec la locale `fr-CH` dans le fuseau `Europe/Zurich`.
- `null` est affiché comme inconnu ou `—`, sans être transformé en métrique réelle.
- L’identité visuelle utilise `assets/ugo2.svg`, sans logo d’instance.
- ECharts est importé dans le bundle Angular et rendu en SVG ; aucun script CDN n’est chargé.

## Validation

```bash
cd web/ihm
npm ci
npm audit --omit=dev --audit-level=moderate
npm run build
```

Le build de production est généré dans `web/src/front/`. Les tests unitaires et end-to-end de ces parcours restent à compléter dans la CI.
