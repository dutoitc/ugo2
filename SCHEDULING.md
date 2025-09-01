# Scheduling & Quota Strategy

## Pipelines
- **Discovery**: enumerate new/changed content (pages/channels/users; WP posts). Lower frequency.
- **Stats**: fetch metrics for recent items (views/engagement). Higher frequency, decays over time.

## Cadences
- **J0 (publication day)**: 11:00, 11:15, 11:30, 12:00, 12:30, 13:00, 13:30, 14:00, 15:00, 16:00, 17:00, 18:00, 19:00, 20:00, 21:00, 22:00.
- **J+1..J+7**: 06:00, 09:00, 12:00, 15:00, 18:00, 21:00.
- **Post J+7**: 1–2 times/day.
- **After 1 month**: weekly.

## Quotas & resume
- Use **If-None-Match / ETag** aggressively; 304 does not consume meaningful quota.
- Persist **checkpoints** (per platform, per tenant): last page cursor / last published date / last processed ID.
- If rate-limited or quota exhausted: **exponential backoff** + jitter; store unfinished range and **resume** next slot.

## Discovery vs Stats split
- Discovery pulls: last N items since last checkpoint (+48h margin).
- Stats pulls: focus on last 7 days (denser), then decay cadence; older items grouped into weekly batches.

## Storage parsimony
- Insert snapshot only if `(relative delta >= 1%) OR (absolute delta >= 10 views)`.
- Always ensure **one daily guard snapshot** to track trends.

## Stars (gradient)
- Compute per-source **24h growth** vs **7-day median**; thresholds drive ⭐/⭐⭐.
- Thresholds are **configurable** in tenant config.

## Alerts
- **Milestones** every **1000** views (configurable) for each source and for the total without WP.
- **Stale stats** (>24h) and **token expiry** (401/403).
