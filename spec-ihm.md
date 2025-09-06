# UGO2 — Spécification IHM (v1)

> **But**: définir l’interface (écrans, APIs front↔back, techno, règles métiers et plan de réalisation) pour un front simple, maintenable et performant, adapté smartphone & grand écran.
>
> **Contexte**: DB existante (`video`, `source_video`, `metric_snapshot`, …). Heures DB en UTC. Affichage Europe/Zurich.

---

## 0. Sommaire

1. Écrans & contenus
2. API (contrats succincts + exemples)
3. Technologies retenues
4. Règles métiers (étoiles, baselines, calculs)
5. UX/UI & design system
6. Performance & architecture front
7. Points d’attention
8. Checklist de réalisation (étapes déployables)
9. Tests & validation
10. Déploiement & structure du repo

---

## 1) Écrans & contenus

### 1.1 Dashboard “Vidéos” (principal)

**Route**: `/`

**Objectif**: liste paginée de toutes les vidéos, colonnes triables/filtrables, avec étoiles de tendance.

**Colonnes (visibles desktop, prioritaires mobile en gras)**

* **Titre** (clic → détail) + badges **★/★★** (tendance) + badge **Type** (*REEL* / *VIDEO*)
* **Publiée le** (local TZ)
* **FB vues** (3s ou vues standard selon source)
* **YT vues**
* **IG vues**
* **WP vues** *(affichées mais **exclues** du total)*
* **Total** *(= FB + YT + IG, **sans WP**)*
* **Dernière mesure** (horodatage le plus récent toutes sources confondues)

**Tri/Filtre**

* Tri multi (ex: Total desc, Publiée le desc)
* Filtres par plateforme présente/absente, min vues, plage de publication, Type (REEL/VIDEO), recherche texte sur titre
* Choix des colonnes visibles

**Interactions**

* Clic sur titre → `/v/:id`
* Icône comparateur → ajoute la vidéo au comparateur (max 3)
* Lien “Alerte & tendance” → `/alerts`

---

### 1.2 Détail vidéo

**Route**: `/v/:id`

**Contenu**

* En-tête: titre, description (brève), **publication** (local TZ), **dernière mesure** (par source), **Type** (REEL/VIDEO)
* **Cartes par source** (FB, YT, IG, WP)

    * KPIs récents: vues totales, engagement (likes+comments+shares+réactions), watch time si dispo
    * **Graphique 72h**: vues en fonction du temps (agrégation 1h) — zoom/hover
    * **Graphique “lifetime”**: vues en fonction du temps (agrégation 1j)
* **Table “Dernières métriques”** (par source): horodatage, vues cumulées, deltas 24h/48h, engagement détaillé
* Boutons: “Comparer” (ouvre `/compare` avec cette vidéo sélectionnée)

---

### 1.3 Dashboard “Overview/KPIs par source”

**Route**: `/overview`

**Contenu**

* Tuiles par source (FB, YT, IG, WP):

    * **# vidéos actives** (≥1 snapshot)
    * **Vues totales** (lifetime)
    * **Durée totale vue** (sec)
    * **Durée moyenne** (sec/vidéo ou sec/vue selon dispo)
    * **Engagement total** (somme)
    * **Dernière mesure** (horodatage)
* Mini-graphiques (sparklines) par source: vues 30 derniers jours

---

### 1.4 Dashboard “Engagement détaillé” (table large)

**Route**: `/engagement`

**Objectif**: vue exhaustive à colonnes serrées.

**Colonnes** (par vidéo, agrégées “dernier snapshot” par source)

* Titre
* Type (REEL/VIDEO)
* Publication
* Par source: Vues, Watch time, **Likes**, **Loves/❤︎**, **Comments**, **Shares**, **Reactions totales** (FB), **CTR** (si dispo YT), etc.
* Total engagement (somme multi-sources avec normalisation simple)

**UI**: table dense, faible padding, scroll horizontal, colonnes gelées (Titre, Publication).

---

### 1.5 Alerte & tendance (Top list)

**Route**: `/alerts`

**Contenu**

* Top N vidéos en accélération (★/★★), avec raison du signal:

    * Δ vues 24h (et %), sources concernées
    * Antéchronologique (les plus “chaudes” en haut)
* Filtres: dernière 24/48/72h, par source, min vues

---

### 1.6 Comparateur de vidéos

**Route**: `/compare`

**Par défaut**: dernière vidéo publiée déjà sélectionnée.

**Fonctions**

* Sélection 2–3 vidéos à superposer
* **Alignement**: “t0 = publication” pour chaque vidéo
* **Baselines**: courbes de référence calculées sur:

    * *Toutes* les vidéos,
    * Les 6 derniers mois,
    * La dernière année
* Sources: choisir la/les sources à afficher (FB/YT/IG)
* Sorties: courbes cumulées (vues) + option 72h/lifetime

---

### 1.7 Explorateur de métriques (debug)

**Route**: `/metrics/explore`

**Contenu**

* Formulaire: videoId (ou slug), source, période, métrique
* Table paginée des `metric_snapshot` (horodatage UTC, valeur brute, métadonnées)

---

### 1.8 Santé des collectes

**Route**: `/health`

**Contenu**

* Par source: dernière mesure, latence moyenne, taux d’erreurs (si exposés par back), fenêtres manquantes
* Indications: “aucun nouveau snapshot depuis X h”

---

## 2) API — Contrats succincts

> **Prefix**: `/api/v1` — JSON only. Tous les timestamps en **UTC** ISO-8601 (`Z`). Les agrégats de temps renvoient des points ordonnés.

### 2.1 Liste des vidéos (Dashboard principal)

`GET /videos`

**Query**: `page`, `pageSize`, `sort` (ex: `total_views:desc`), `q` (titre), `type` (`REEL|VIDEO|ANY`), `has` (`FB,YT,IG,WP`), `publishedFrom`, `publishedTo`

**Réponse**

```json
{
  "page": 1,
  "pageSize": 50,
  "total": 123,
  "items": [
    {
      "id": 42,
      "title": "Fort du Pré-Giroud - visite",
      "published_at": "2025-05-16T18:30:00Z",
      "last_measure_at": "2025-09-06T06:10:00Z",
      "type": "VIDEO",
      "views": { "FB": 1250, "YT": 3100, "IG": 420, "WP": 200 },
      "total_views_excl_wp": 4770,
      "sources": ["FB","YT","IG"],
      "trend": { "stars": 2, "delta24h": 380, "deltaPct24h": 12.4 }
    }
  ]
}
```

> **Note**: `trend` peut être calculé côté front si non fourni. Idéalement, le back calcule `delta24h` par source puis agrégé.

---

### 2.2 Détail vidéo

`GET /videos/{id}`

**Réponse**

```json
{
  "id": 42,
  "title": "...",
  "description": "...",
  "published_at": "2025-05-16T18:30:00Z",
  "type": "VIDEO",
  "sources": [
    {
      "platform": "YT",
      "platform_id": "abcd1234",
      "last_measure_at": "2025-09-06T06:10:00Z",
      "latest": {
        "views": 3100,
        "engagement": {"likes": 120, "comments": 12, "shares": 3, "reactions": 0},
        "watch_time_seconds": 18000
      }
    },
    { "platform": "FB", "platform_id": "...", "last_measure_at": "...", "latest": { "views": 1250, "engagement": {"reactions": 80, "comments": 7, "shares": 5} } }
  ]
}
```

---

### 2.3 Séries temporelles (par vidéo & source)

`GET /videos/{id}/metrics`

**Query**: `source=FB|YT|IG|WP`, `metric=views|engagement|watch_time`, `from`, `to`, `bucket=1h|6h|1d`

**Réponse**

```json
{
  "source": "YT",
  "metric": "views",
  "bucket": "1h",
  "points": [
    {"t": "2025-09-03T10:00:00Z", "v": 120},
    {"t": "2025-09-03T11:00:00Z", "v": 150}
  ]
}
```

> **Remarque**: cumul ou incrément ? Recommandé: **cumul** (plus simple à lire). Le front pourra dériver Δ si nécessaire.

---

### 2.4 KPIs par source (Overview)

`GET /overview`

**Réponse**

```json
{
  "sources": [
    {
      "source": "FB",
      "videos": 120,
      "views_total": 250000,
      "watch_time_seconds_total": 820000,
      "watch_time_seconds_avg": 6800,
      "engagement_total": 5400,
      "last_measure_at": "2025-09-06T06:10:00Z"
    },
    {"source":"YT", "videos":110, "views_total":310000, "watch_time_seconds_total": 920000, "watch_time_seconds_avg": 7600, "engagement_total": 6100, "last_measure_at":"..."}
  ]
}
```

---

### 2.5 Alerts & Trends (Top list)

`GET /alerts`

**Query**: `window=24h|48h|72h`, `minViews`, `sources`, `limit=20`

**Réponse**

```json
{
  "window": "24h",
  "items": [
    {
      "video_id": 42,
      "title": "...",
      "stars": 2,
      "reasons": [
        {"source":"YT", "delta": 300, "deltaPct": 11.3},
        {"source":"FB", "delta": 120, "deltaPct": 18.0}
      ]
    }
  ]
}
```

---

### 2.6 Comparateur & baselines

* **Dernière vidéo publiée**: `GET /videos:last`
* **Courbes comparées**: `GET /compare`
  **Query**: `videos=42,51,63`, `sources=YT,FB`, `mode=72h|lifetime`, `align=publication`
* **Baseline**: `GET /metrics/baseline`
  **Query**: `window=all|180d|365d`, `sources=YT,FB,IG`, `align=publication`

**Réponse baseline**

```json
{
  "window":"365d",
  "align":"publication",
  "curves":[
    {"source":"YT", "points":[{"t":"PT0H","v":0},{"t":"PT24H","v":300}, {"t":"P2D","v":520}]},
    {"source":"FB", "points":[{"t":"PT0H","v":0},{"t":"PT24H","v":180} ]}
  ]
}
```

> Ici `t` est un **offset** (ISO-8601 duration) depuis t0 (publication), pour permettre l’alignement.

---

### 2.7 Explorateur de métriques

`GET /metrics:explore`

**Query**: `videoId`, `source`, `metric`, `from`, `to`, `page`, `pageSize`

**Réponse**: table brute des snapshots, selon le schéma DB.

---

### 2.8 Santé des collectes

`GET /health`

**Réponse**

```json
{
  "sources": [
    {"source":"FB","last_measure_at":"...","missing_windows":0,"error_rate":0.01},
    {"source":"YT","last_measure_at":"...","missing_windows":2,"error_rate":0}
  ]
}
```

---

## 3) Technologies retenues


### 3.1 Choix principaux (verrouillés)

* **Framework**: Angular **20.x** (standalone components, Signals, Router v20)
* **Langage**: TypeScript **5.5+** (ES2022 target)
* **Tables**: **AG Grid Community** (packages: `ag-grid-community`, `ag-grid-angular`) — tri/filtre/pagination server-side, colonnes gelées, densité élevée
* **Graphiques**: **Apache ECharts** via **`ngx-echarts`** (packages: `echarts` `ngx-echarts`) — zoom, tooltips, séries multiples, bonnes perfs
* **Dates/TZ**: **Luxon 3.x** (`luxon`) — conversion UTC → Europe/Zurich, formatage local, durations
* **HTTP**: Angular `HttpClient` + Interceptor (headers communs, gestion erreurs)
* **État**: **Angular Signals** (stores maison légers) + RxJS 7.8 (streams HTTP)
* **Styles**: **SCSS** (variables + mixins). **Pas de Bootstrap**. Thème perso (bleu logo CAPStv)
* **Icônes**: **Material Symbols** (webfont) — pas de dépendance `@angular/material` obligatoire
* **i18n**: Angular i18n (fr par défaut), nombres via `Intl.NumberFormat`

### 3.2 Outils & qualité

* **CLI**: Angular CLI `@angular/cli` 20.x
* **Lint**: ESLint 9 + `angular-eslint`
* **Format**: Prettier 3.x
* **Tests unitaires**: **Jest** 29.x (`jest`, `jest-preset-angular`) — plus rapide que Karma
* **E2E**: **Playwright** 1.x (routes clés et scénarios critiques)
* **Git hooks** (optionnel): `husky` + `lint-staged`

### 3.3 Cibles & compatibilité

* **Node**: 20 LTS
* **Navigateurs**: Chrome/Edge/Firefox/Safari (2 dernières versions), iOS Safari 15+, Android Chrome 110+
* **Rendu**: Responsive mobile-first; tables denses en desktop avec colonnes gelées

### 3.4 Paquets NPM — récapitulatif

| Rôle               | Package                                             | Version cible     | Remarques                   |
| ------------------ | --------------------------------------------------- | ----------------- | --------------------------- |
| Framework          | `@angular/core` `@angular/router` `@angular/common` | ^20.0.0           | Standalone + Signals        |
| CLI                | `@angular/cli`                                      | ^20.0.0           | Build/serve/test            |
| Tables             | `ag-grid-community` `ag-grid-angular`               | ^32.0.0           | Community edition           |
| Graphes            | `echarts` `ngx-echarts`                             | ^5.5.0 / ^16.0.0  | ECharts 5 + wrapper Angular |
| Dates              | `luxon`                                             | ^3.5.0            | TZ Europe/Zurich            |
| Lint               | `eslint` `@angular-eslint/*`                        | ^9.0.0            | Règles Angular              |
| Format             | `prettier`                                          | ^3.3.0            |                             |
| Tests unitaires    | `jest` `jest-preset-angular`                        | ^29.7.0 / ^14.0.0 | Config Angular Jest         |
| E2E                | `@playwright/test`                                  | ^1.46.0           |                             |
| Utils (optionnels) | `zod`                                               | ^3.23.0           | Validation légère d’IO API  |

### 3.5 Alternatives considérées

* **Angular Material**: gardé **en réserve** (non requis). Si besoin d’un composant spécifique (dialog, menu, tooltip) non trivial, on pourra ajouter `@angular/material` ciblé (sans importer tout le design system)
* **PrimeNG**: puissant (DataTable), mais lock-in + theming spécifique; AG Grid + SCSS suffisent ici
* **Bootstrap**: écarté pour éviter collision CSS et dépendances inutiles

### 3.6 Snippet `package.json` (extrait de base)

```json
{
  "engines": { "node": ">=20" },
  "dependencies": {
    "@angular/animations": "^20.0.0",
    "@angular/common": "^20.0.0",
    "@angular/compiler": "^20.0.0",
    "@angular/core": "^20.0.0",
    "@angular/forms": "^20.0.0",
    "@angular/platform-browser": "^20.0.0",
    "@angular/platform-browser-dynamic": "^20.0.0",
    "@angular/router": "^20.0.0",
    "ag-grid-angular": "^32.0.0",
    "ag-grid-community": "^32.0.0",
    "echarts": "^5.5.0",
    "ngx-echarts": "^16.0.0",
    "luxon": "^3.5.0"
  },
  "devDependencies": {
    "@angular/cli": "^20.0.0",
    "@angular-devkit/build-angular": "^20.0.0",
    "@playwright/test": "^1.46.0",
    "@types/jest": "^29.5.12",
    "angular-eslint": "^19.0.0",
    "eslint": "^9.0.0",
    "jest": "^29.7.0",
    "jest-preset-angular": "^14.0.0",
    "prettier": "^3.3.0",
    "typescript": "~5.5.0"
  }
}

---

## 4) Règles métiers & calculs

### 4.1 Étoiles de tendance (★ / ★★)

Pour chaque vidéo, calculer sur **72h** glissantes, fenêtre par défaut **24h** (configurable):

* `Δviews_24h = views(t_now) – views(t_now-24h)` (par source → puis agrégation max ou somme; recommandé: **max** des sources pour l’icône globale)
* **★** si `Δviews_24h ≥ 100` **ou** `(views_now ≥ 200 && croissance_24h ≥ 20%)`
* **★★** si `Δviews_24h ≥ 300` **ou** `(views_now ≥ 500 && croissance_24h ≥ 35%)`
* Pour petites vidéos (`views_now ≤ 100`): utiliser **%** uniquement (seuils: 30% → ★, 60% → ★★)
* Si pas de point à t-24h, interpoler entre voisinage (ou tomber en N/A → pas d’étoile)

### 4.2 Totaux

* `total_views_excl_wp = FB + YT + IG` (WP **exclu** du total)
* Engagement total: normalisation simple = somme des items pertinents par source (FB: reactions+comments+shares; YT: likes+comments; IG: likes+comments; adapter si d’autres mesures sont dispo).

### 4.3 Graphes

* **72h**: bucket `1h` (downsampling côté back recommandé)
* **Lifetime**: bucket `1d` (ou adaptatif si >2 ans)
* Séries **cumulées** (plus lisibles). Afficher Δ/jour en tooltip.

### 4.4 Comparateur & baseline

* Alignement t0 = `published_at`
* Baselines: moyenne ou médiane par source des courbes normalisées (t0→t+72h et t0→lifetime). Par défaut: **médiane** (moins sensible aux outliers).

---

## 5) UX/UI & design system

* Thème bleu (couleurs proches logo CAPStv). Variables CSS: `--primary`, `--primary-600`, `--accent`, `--bg`, `--text`.
* Layout responsive:

    * Mobile: colonnes essentielles (Titre, Type, Total, Dernière mesure). Toggle “+” pour voir les plateformes.
    * Desktop: colonnes complètes, sticky header et colonnes gelées.
* Icônes: Material Icons (étoiles, sources FB/YT/IG, comparateur, santé).
* Accessibilité: contraste ≥ WCAG AA, taille de police min 14px table dense.

---

## 6) Performance & architecture front

* Pagination **server-side** pour les listes (éviter de charger 250×N metrics).
* Endpoints de **résumé** (latest + delta24h) pour lister rapidement sans N requêtes secondaires.
* Cache mémoire léger par route (invalidate quand on revient d’un détail).
* ChangeDetection `OnPush`, `trackBy` pour listes, virtual scroll au besoin.
* Lazy-loading des pages graphiques.

---

## 7) Points d’attention

* **Timezone**: DB en UTC → conversion **affichage** Europe/Zurich; tooltips affichent heure locale.
* **Hétérogénéité des métriques**: FB/IG “reels” vs “videos” (nom de métrique différent). Documenter le mapping dans le back.
* **Données manquantes**: interpoler prudemment (afficher “lacune de mesure” visuellement sur les graphes si trous > 3h).
* **WordPress**: afficher mais **exclure** du total (libellé clair).
* **Évolutivité**: prévoir ajout TikTok/LinkedIn (noms de source génériques, colonnes dynamiques).

---
8) Checklist (voir TODO)

---

## 9) Tests & validation

* **Unitaires**: services (adapters JSON), calcul étoiles, conversions TZ.
* **Intégration**: contrats d’API (schémas), mocks HTTP.
* **E2E léger**: routes clés (`/`, `/v/:id`, `/overview`).
* **Manuels**: pagination (volumes 250 vidéos), temps de rendu < 150ms scroll, graphes 72h.

**Exemples cURL (à adapter au back)**

```bash
# Liste vidéos
curl -s "https://ugo2.capstv.ch/api/v1/videos?page=1&pageSize=50&sort=total_views:desc"

# Détail + séries
curl -s "https://ugo2.capstv.ch/api/v1/videos/42"
curl -s "https://ugo2.capstv.ch/api/v1/videos/42/metrics?source=YT&metric=views&from=2025-09-03T00:00:00Z&to=2025-09-06T00:00:00Z&bucket=1h"

# Overview
curl -s "https://ugo2.capstv.ch/api/v1/overview"

# Alerts
curl -s "https://ugo2.capstv.ch/api/v1/alerts?window=24h&limit=20"

# Comparateur & baseline
curl -s "https://ugo2.capstv.ch/api/v1/videos:last"
curl -s "https://ugo2.capstv.ch/api/v1/metrics/baseline?window=365d&sources=YT,FB&align=publication"

# Explorateur
curl -s "https://ugo2.capstv.ch/api/v1/metrics:explore?videoId=42&source=YT&metric=views&from=2025-09-01T00:00:00Z&to=2025-09-06T00:00:00Z&page=1&pageSize=200"

# Santé
curl -s "https://ugo2.capstv.ch/api/v1/health"
```

---

## 10) Déploiement & structure repo

**Arbo front** (dans `web/src/front`)

```
src/
  app/
    core/ (services: videos, metrics, overview, alerts, health, time)
    shared/ (pipes, directives, components génériques)
    pages/
      dashboard/
      video-detail/
      overview/
      engagement/
      alerts/
      compare/
      metrics-explore/
      health/
    app.routes.ts
    app.component.ts
  assets/
    theme.scss (variables bleu logo)
```

**Build**

* `ng build --configuration production` → output vers `web/src/front/dist` (adapter au hosting actuel)
* `.htaccess` : reste inchangé (SPA). `base href="/"` et appels API relatifs `/api/v1/...`
* Cache: index.html **no-cache**, assets versionnés (hashs).

---

### Annexes — Mapping engagement par source (suggestion)

* **Facebook**: `reactions_total` = somme des réactions (like/love/anger/…); `engagement = reactions_total + comments + shares`
* **YouTube**: `engagement = likes + comments` (shares si dispo via API)
* **Instagram**: `engagement = likes + comments`
* **WordPress**: pas d’engagement standard (affichable séparément si disponible)
