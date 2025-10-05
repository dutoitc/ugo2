# TODO / Roadmap

## IHM

## Prompt
But: modifier xxx

Contraintes fortes (à respecter à la lettre) :
- Ne modifie que ce qui est nécessaire pour les opération sdemandées. Pas de refactoring ni de changements “cosmétiques” (formatage, renommages, réordonnancement) sauf si indispensable pour compiler/faire fonctionner.
- Préserve l’ordre des imports, variables, méthodes, sections. Ne trie pas les imports. Ne change pas les guillemets, l’indentation ni les sauts de ligne.
- N’ajoute ni ne supprime de dépendances. Ne modifie pas package.json/lock.
- Pas de reformatage automatique (ESLint/Prettier). Pas d’espaces de fin, pas de retours chariot inutiles.
- Garde les en-têtes/licences, les commentaires existants et le style du fichier.
- Si une donnée de l’étape est ambiguë, prends la décision minimale et explique-la dans “Hypothèses”.
Sortie attendue :
- Résumé des changements (très court)
- Liste des fichiers modifiés (chemins relatifs)
- Hypothèses/contraintes (si nécessaire, concis)
- Code complet de chaque fichier modifié (et uniquement ceux-là), dans des blocs code séparés, précédés du chemin exact.
- Tests/validation manuelle (checklist très courte).
- Améliorations possibles (optionnel, après la livraison, sans les coder maintenant).
Important :
- Si une compilation échoue sans un micro-fix de type/nullable/import, applique le fix minimal et documente-le en 1 ligne.
- Pas de renvoi partiel : pour chaque fichier touché, donne l’intégralité du fichier.
- Pas de texte avant/après le code autre que les sections 1–6.
Format de réponse :
### 1. Résumé
### 2. Fichiers modifiés
### 3. Hypothèses
### 4. Code
path/to/file.ext
…contenu complet…
### 5. Validation
### 6. Améliorations





## 8) Checklist de réalisation — étapes déployables
> Chaque étape produit une version utilisable, commitable et déployable.

- [x] Étape 0 — Scaffolding & thème**
  * Angular app, routing, thème bleu, layout header/sidebar.
  * Healthcheck appel `/api/health` et affichage basic.

- [x] Étape 1 — Dashboard principal (liste)**
  * `GET /videos` (mock → réel) avec colonnes FB/YT/IG/WP, Total, Type, Dernière mesure.
  * Tri/filtre basiques, pagination server-side.
  * Badge étoiles (calcul front provisoire: delta24h via endpoint additionnel ou désactivé si absent).

- [x] Étape 2 — Détail vidéo**
  * `GET /videos/{id}` + séries `GET /videos/{id}/metrics` (72h + lifetime).
  
- [ ] Étape 2b
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