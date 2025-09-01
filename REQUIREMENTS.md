# UGO2 — Requirements (EN)

## Scope
- Track video performance across **Facebook**, **YouTube**, **Instagram** and **WordPress** (WP excluded from totals).
- Two independent **tenants** (webTVs), each with its **own DB** and **own PHP UI**; tenants may have different sources (IG optional).
- Batch-first architecture; CLI profiles for initial import and periodic updates.

## Functional requirements
1. **Canonical video grouping** across platforms using fuzzy title/description, time window (default −72h/+24h), duration tolerance, and teaser heuristics.
2. **Official date** of a canonical video = earliest publication date among its grouped sources (teasers included).
3. **Metrics** captured:
   - Views (3s reference on FB; YT `viewCount` and IG `video_views`/`plays` used as **3s equivalents**).
   - Engagement: comments, reactions/likes, shares, saves (IG).
   - Trends: 24h/7d/28d, top videos, top presenters/directors.
4. **Admin console**:
   - Manual overrides: LINK/UNLINK sources, mark teaser/main.
   - Edit presenter/director, geolocation (lat/lon), tags.
   - Audit logs.
5. **Public pages**:
   - All videos table: per-source views + **Total (excluding WP)** with filters/sort/pagination.
   - Aggregates per **presenter** and **director**.
   - **Map** (Leaflet; clusters/filters in v2).
6. **Alerts**:
   - Token expiry (FB/YT/IG) and **stale stats** (>24h).
   - **Milestones** every **1000** views (configurable) per source and total (excluding WP).
7. **Stars (growth indicator)**:
   - 1–2 stars next to per-source views when **24h growth** exceeds configurable thresholds vs 7-day median.

## Non-functional requirements
- **Quotas**: If-None-Match/ETag, strict fields/parts, batching, exponential backoff with jitter, **resume checkpoints** when quotas are exhausted.
- **Storage parsimony**: Insert snapshot only if `(relative delta ≥ 1%) OR (absolute delta ≥ 10 views)`; still guarantee one **daily guard snapshot**.
- **Scheduling**:
  - **J0**: 11:00, 11:15, 11:30, 12:00, 12:30, 13:00, 13:30, 14:00, 15:00, 16:00, 17:00, 18:00, 19:00, 20:00, 21:00, 22:00.
  - **J+1..J+7**: 06:00, 09:00, 12:00, 15:00, 18:00, 21:00.
  - **>J+7**: 1–2/day; **>1 month**: weekly.
  - Discovery cadence < Stats cadence.
- **Security**: simple PHP signup + manual `admin` flag in DB; secrets outside VCS; minimal rate limiting per platform.
- **Observability**: timers for API calls, counters for quota, error rates; email alerts.
- **Internationalization**: FR-first UI copy; internal docs in EN/FR as needed.

## API usage (minimal fields)
- **Facebook Graph v23**:
  - `/{page-id}/published_posts?fields=id,created_time,permalink_url,attachments{media_type,target{id}}&limit=100`
  - `/{video-id}?fields=length,description,title,created_time,permalink_url`
  - `/{video-id}/video_insights/total_video_views/lifetime`
  - `/{post-id}?fields=shares,comments.limit(0).summary(true),reactions.limit(0).summary(true)`
- **Instagram Graph**:
  - `/{ig-user-id}/media?fields=id,caption,media_type,media_product_type,permalink,thumbnail_url,timestamp&limit=50`
  - `/{media-id}/insights?metric=impressions,reach,video_views,plays,saves,comments,likes`
  - Only **videos** and **Reels** are in scope (no pure photo posts).
- **YouTube Data API v3**:
  - `search.list?channelId={id}&type=video&order=date&publishedAfter={iso}&maxResults=50&fields=items(id(videoId),snippet(publishedAt,title,description))`
  - `videos.list?part=snippet,contentDetails,statistics&id={ids}&fields=items(id,etag,snippet(publishedAt,title,description,channelId),contentDetails(duration),statistics(viewCount,likeCount,commentCount))`
- **WordPress REST**:
  - `wp-json/wp/v2/posts?per_page=50&_fields=id,date,link,title,excerpt,slug`

## Tenancy & deployment
- One **MariaDB** per tenant; PHP UI deployed per tenant; batch launched per tenant with its own config.
- Containerization later via docker-compose (DB + batch + PHP).

## Open points / TBD
- Daily API budgets per platform (max calls/day) per tenant.
- Final license confirmation (**AGPL-3.0-or-later** recommended to prevent closed SaaS forks; still allows commercial use).
- Exact thresholds for stars (gradient) per platform.

## Acceptance criteria (doc-only PR)
- Documents present and coherent (README, REQUIREMENTS, SCHEDULING, CONFIGURATION, TODO, CHANGELOG).
- Scheduling reflects provided cadences.
- Non-goals and normalization policy are explicit.
