# UGO2 — Suivi multi-plateformes des vues vidéo (FB / YT / IG / WP)

**Objectif** : agréger, réconcilier et suivre les **vues** (référence 3s FB, équivalents YT/IG), l’**engagement** (commentaires, réactions/likes, partages, saves) et les **tendances** (24h/7j/28j) pour des vidéos publiées sur **Facebook**, **YouTube**, **Instagram** et **WordPress** (WP non inclus dans les totaux).

## Portée
- Plusieurs webTV **indépendantes** : **1 DB/tenant** et **1 UI PHP/tenant**, sélection par fichier de configuration à l’exécution.
- Sources par tenant : YT & FB obligatoires, IG **optionnel**, WP pour référence (hors somme).

## Architecture (aperçu)
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

## Réconciliation (résumé)
- Matching fuzzy **titre/description**, proximité **±72h**, tolérance durée ±30s, heuristiques **teaser** (mots-clés, durée courte, antériorité 2–3 jours).
- Les overrides admin priment (link/unlink, teaser/main).
- **Date officielle** = première date de publication **du cluster** (teasers inclus).

## Normalisation “vues 3s”
- **FB** : `total_video_views/lifetime` = référence 3s (canonique).
- **YT** : `statistics.viewCount` (pas 3s) ⇒ **équivalent** documenté.
- **IG** : `video_views` (vidéos) / `plays` (Reels) ⇒ **équivalents** 3s.
- **WP** : exclu de la somme, affichage séparé.

## Cadences & quotas
- **J0** (publication) : 11:00, 11:15, 11:30, 12:00, 12:30, 13:00, 13:30, 14:00, 15:00, 16:00, 17:00, 18:00, 19:00, 20:00, 21:00, 22:00.
- **J+1 à J+7** : 06:00, 09:00, 12:00, 15:00, 18:00, 21:00.
- **> J+7** : 1–2×/jour. **> 1 mois** : 1×/semaine.
- Pipelines **séparés** : Discovery (liste contenus, moins fréquent) vs Stats (métriques, plus fréquent).
- Reprise quotas : checkpoints persistants + backoff exponentiel + **If-None-Match/ETag**.

## Parcimonie de stockage
- **Snapshot** inséré **seulement si** `deltaRel ≥ 1%` **ou** `deltaAbs ≥ 10 vues` (paramétrables).
- **Garde-fou** : 1 snapshot/jour pour chaque source afin d’éviter des trous de série.

## Étoiles (gradient)
- ⭐ / ⭐⭐ à côté des vues **par source** quand la **croissance 24h** excède un **seuil** vs médiane 7j (paramètres dans `CONFIGURATION.md`).

## Alertes
- **Palier vues** toutes les **1000** (paramétrable), par source et total (hors WP).
- **Token expiré** (401/403) et **stats non mises à jour** depuis 24h.

## Build & Run (squelette)
- **Prérequis : JDK 21** (et Maven récent). Vérifie avec `mvn -v`.
- Prépare un fichier `src/main/resources/application.properties` **hors-git** à partir de `application.properties.tmpl`.
```bash
mvn -q -DskipTests package
# exemples CLI (squelette)
java -jar target/ugo2-0.2.0-SNAPSHOT.jar import:init --modes=both
java -jar target/ugo2-0.2.0-SNAPSHOT.jar update --profile=J0 --mode=stats --since=2025-08-01
```

## Contribution
- **Conventional Commits** (`feat:`, `fix:`, `docs:`, `chore:`…)
- Chaque PR met à jour **README**, **REQUIREMENTS**, **TODO**, **CHANGELOG**.

## Licence
- Recommandation : **AGPL-3.0-or-later** (copyleft fort côté réseau/SaaS).
- À confirmer : GPL/AGPL autorisent l’usage commercial (vente incluse).

## Statut
- PR #1 : **documentation uniquement**.
- PR #2 : **squelette Spring Boot + Flyway + CLI + config binding**.
