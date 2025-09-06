# TODO / Roadmap

## IHM


## 8) Checklist de réalisation — étapes déployables
> Chaque étape produit une version utilisable, commitable et déployable.

- [ ] Étape 0 — Scaffolding & thème**
  * Angular app, routing, thème bleu, layout header/sidebar.
  * Healthcheck appel `/api/health` et affichage basic.

- [ ] Étape 1 — Dashboard principal (liste)**
  * `GET /videos` (mock → réel) avec colonnes FB/YT/IG/WP, Total, Type, Dernière mesure.
  * Tri/filtre basiques, pagination server-side.
  * Badge étoiles (calcul front provisoire: delta24h via endpoint additionnel ou désactivé si absent).

- [ ] Étape 2 — Détail vidéo**
  * `GET /videos/{id}` + séries `GET /videos/{id}/metrics` (72h + lifetime).
  * Graphes ECharts (1h / 1d), cartes par source, table “dernières métriques”.

- [ ] Étape 3 — Overview/KPIs**
  * `GET /overview` tuiles par source + mini-sparklines 30j.

- [ ] Étape 4 — Engagement détaillé**
  * `/engagement` table large (dense), colonnes par source + total engagement, colonnes gelées.

- [ ] Étape 5 — Alerts & Trends**
  * `GET /alerts` (back calcule delta24h/pct par source). Top list ★/★★ avec filtres.
  * Étoiles actives aussi sur dashboard principal (réutilise même calcul/règles).

- [ ] Étape 6 — Comparateur & baselines**
  * `/compare` avec sélection 2–3 vidéos, alignement publication, baseline `GET /metrics/baseline`.
  
- [ ] Étape 7 — Explorateur de métriques**
  * `/metrics:explore` table brute filtrable.

- [ ] Étape 8 — Santé des collectes**
  * `/health` enrichi (par source). UI feux (vert/orange/rouge).

- [ ] Étape 9 — Finitions responsive & perf**
  * Column chooser, sticky headers, virtual scroll si besoin, caches TTL.

- [ ] Étape 10 — Stabilisation & doc**
  * README front, scripts build/deploy, matrices de test.



## Next
- [ ] Add CI (GitHub Actions) build (compile + unit tests).
- [ ] Provide Dockerfile + minimal docker-compose (DB + app).
- [ ] IG media (videos/Reels only), insights.
- [ ] WP posts (reference only).
- [ ] PHP admin/public pages (lists, aggregates, map v1).
- [ ] Alerts email (milestones, stale stats, tokens).
- [ ] Exports CSV/JSON.
- [ ] Heatmap by hours/days
- [ ] Reconciliation admin