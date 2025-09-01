# UGO2  Suivi multi-plateformes des vues vidé(FB / YT / IG / WP)

**Objectif** : agrér, réncilier et suivre les **vues** (rérence 3s FB, éivalents YT/IG), l**engagement** (commentaires, rétions/likes, partages, saves) et les **tendances** (24h/7j/28j) pour des vidé publié sur **Facebook**, **YouTube**, **Instagram** et **WordPress** (WP non inclus dans les totaux).

## Porté- Plusieurs webTV **indéndantes** : **1 DB/tenant** et **1 UI PHP/tenant**, séction par fichier de configuration àexétion.
- Sources par tenant : YT & FB obligatoires, IG **optionnel**, WP pour rérence (hors somme).

## Architecture (aperç


```
[FB Graph v23]  [IG Graph]  [YT Data v3]  [WP REST]
      \             |            |              /
       \------------|------------|-------------/    (ETag, fields/part, quotas)
                    v
                [UGO2 (Batch Boot)]
                - Discovery pipeline (rare)
                - Stats pipeline (dense, dégressif)
                - Reconcile (fuzzy + heuristiques teaser)
                    |
                    v
                [MariaDB par tenant]
                (snapshots parcimonieux, agrégats)
                    |
                    v
                [PHP UI par tenant]
                - Liste vidéos + totaux (sans WP)
                - Présentateurs / Réalisateurs (agrégats)
                - Carte Leaflet (v2: clusters+filtres)
                - Admin : overrides LINK/UNLINK, flags teaser/main
```

## Rénciliation (rémé- Matching fuzzy **titre/description**, proximité*±72h**, durétolénce ±30s, heuristiques **teaser** (mots-clé durécourte, antéorité3 jours).
- Overrides admin priment (link/unlink, teaser/main).
- **Date officielle** = premiè date de publication **du cluster** (teasers inclus).

## Normalisation vues 3s
- **FB** : `total_video_views/lifetime` = rérence 3s.
- **YT** : `statistics.viewCount` (pas 3s) ? **éivalent** documenté- **IG** : `video_views` (vidé) / `plays` (Reels) ? éivalents 3s.
- **WP** : exclu de la somme, affichage séré
## Cadences & quotas
- **J0** (publication) : 11:00, 11:15, 11:30, 12:00, 12:30, 13:00, 13:30, 14:00, 15:00, 16:00, 17:00, 18:00, 19:00, 20:00, 21:00, 22:00.
- **J+1 à+7** : 06:00, 09:00, 12:00, 15:00, 18:00, 21:00.
- **> J+7** : 12×jour. **> 1 mois** : 1×semaine.
- Pipelines **séré* : Discovery (liste contenus, moins fréent) vs Stats (méiques, plus fréent).
- Reprise quotas : checkpoints persistants + backoff exponentiel + **If-None-Match/ETag**.

## Parcimonie de stockage
- **Snapshot** insé **seulement si** `deltaRel = 1%` **ou** `deltaAbs = 10 vues` (paraméables).
- **Garde-fou** : 1 snapshot/jour pour chaque source afin déter des trous de sée.

## Éoiles (gradient)
- ?/**??** àôdes vues **par source** quand la **croissance 24h** excè un **seuil** vs méane 7j (paramèes dans `CONFIGURATION.md`).

## Alertes
- **Palier vues** toutes les **1000** (paraméable), par source et total (hors WP).
- **Token expiré (401/403) et **stats non mises àour** depuis 24h.

## Build & Run (squelette PR2)
- Prére un fichier `src/main/resources/application.properties` **hors-git** àartir de `application.properties.tmpl`.
- requires JDK21
- Build :  
  ```bash
  mvn -q -DskipTests package


