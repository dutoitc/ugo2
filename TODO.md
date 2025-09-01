# TODO / Roadmap

## Sprint 1  Docs & bootstrap (done)
- [x] README (FR), REQUIREMENTS (EN), SCHEDULING, CONFIGURATION, TODO, CHANGELOG.
- [x] Decide license (AGPL-3.0-or-later recommended). *(TBD to apply in repo)*
- [ ] Add issue templates (bug/feature) and PR template.

## Sprint 2  Skeleton & config wiring (this PR)
- [x] Maven + Spring Boot 3.3 skeleton (no business logic).
- [x] Spring Data JDBC baseline.
- [x] Flyway bootstrap with baseline-on-migrate.
- [x] CLI stub (`import:init`, `update --profile`, `--mode=discover|stats|both`).
- [x] Properties binding for thresholds (delta, milestones, stars).
- [ ] Add CI (GitHub Actions) build (compile + unit tests).
- [ ] Provide Dockerfile + minimal docker-compose (DB + app).

## Sprint 3  Ingestion (APIs) & reconciliation
- [ ] FB v23 discovery (posts?videos), insights (3s), engagement in compact calls.
- [ ] YT search/videos minimal fields, ETag handling.
- [ ] IG media (videos/Reels only), insights.
- [ ] WP posts (reference only).
- [ ] Reconcile service (fuzzy + heuristics), overrides format.

## Sprint 4  UI PHP & observability
- [ ] PHP admin/public pages (lists, aggregates, map v1).
- [ ] Alerts email (milestones, stale stats, tokens).
- [ ] Prometheus metrics, logs JSON.
- [ ] Exports CSV/JSON.

## Governance
- Conventional Commits; update README/REQUIREMENTS/TODO/CHANGELOG each PR.

