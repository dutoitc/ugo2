# TODO / Roadmap

## Sprint 1 — Docs & bootstrap (this PR)
- [x] README (FR), REQUIREMENTS (EN), SCHEDULING, CONFIGURATION, TODO, CHANGELOG.
- [ ] Decide license (AGPL-3.0-or-later recommended).
- [ ] Add issue templates (bug/feature) and PR template.

## Sprint 2 — Skeleton & config wiring
- [ ] Maven + Spring Boot 3.3 skeleton (no business logic).
- [ ] Spring Data JDBC baseline, Flyway with per-tenant DB naming convention.
- [ ] CLI stub (`import:init`, `update --profile`, `--mode=discover|stats`).
- [ ] Properties binding for thresholds (delta, milestones, stars).

## Sprint 3 — Ingestion (APIs) & reconciliation
- [ ] FB v23 discovery (posts→videos), insights (3s), engagement in compact calls.
- [ ] YT search/videos minimal fields, ETag handling.
- [ ] IG media (videos/Reels only), insights.
- [ ] WP posts (reference only).
- [ ] Reconcile service (fuzzy + heuristics), overrides format.

## Sprint 4 — UI PHP & observability
- [ ] PHP admin/public pages (lists, aggregates, map v1).
- [ ] Alerts email (milestones, stale stats, tokens).
- [ ] Prometheus metrics, logs JSON.
- [ ] Exports CSV/JSON.

## Governance
- Conventional Commits; update README/REQUIREMENTS/TODO/CHANGELOG each PR.
