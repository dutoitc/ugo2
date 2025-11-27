DROP VIEW IF EXISTS `v_metric_snapshot_enriched`;

CREATE VIEW `v_metric_snapshot_enriched` AS
SELECT
  ms.id,
  ms.source_video_id,
  sv.video_id,
  sv.platform,
  sv.platform_format,
  sv.platform_video_id,
  sv.url,
  sv.title        AS source_title,
  sv.published_at AS source_published_at,
  v.title         AS video_title,
  v.published_at  AS video_published_at,

  ms.snapshot_at,

  -- bruts
  ms.views_native,
  ms.avg_watch_seconds,
  ms.total_watch_seconds,
  COALESCE(ms.video_length_seconds, sv.duration_seconds, v.duration_seconds) AS length_seconds,
  ms.reach,
  ms.unique_viewers,
  ms.likes,
  ms.comments,
  ms.shares,
  ms.reactions_total,
  ms.reactions_like,
  ms.reactions_love,
  ms.reactions_wow,
  ms.reactions_haha,
  ms.reactions_sad,
  ms.reactions_angry,

  -- NEW : vue native fallback (important pour Facebook Reels)
  COALESCE(ms.views_native, ms.reach) AS views_native_fallback,

  -- ratios dérivés
  CASE
    WHEN COALESCE(ms.video_length_seconds, sv.duration_seconds, v.duration_seconds, 0) = 0
      THEN NULL
    ELSE ms.avg_watch_seconds /
         COALESCE(ms.video_length_seconds, sv.duration_seconds, v.duration_seconds)
  END AS avg_watch_ratio,

  CASE
    WHEN COALESCE(ms.video_length_seconds, sv.duration_seconds, v.duration_seconds, 0) = 0
      THEN NULL
    ELSE ms.total_watch_seconds /
         COALESCE(ms.video_length_seconds, sv.duration_seconds, v.duration_seconds)
  END AS watch_equivalent,

  CASE
    WHEN COALESCE(ms.views_native, ms.reach, 0) = 0
      THEN NULL
    ELSE (ms.likes + ms.comments + ms.shares) /
         COALESCE(ms.views_native, ms.reach)
  END AS engagement_rate

FROM metric_snapshot ms
JOIN source_video sv ON sv.id = ms.source_video_id
LEFT JOIN video v ON v.id = sv.video_id;



DROP VIEW IF EXISTS `v_source_latest_snapshot`;

CREATE VIEW `v_source_latest_snapshot` AS
SELECT ms.*
FROM metric_snapshot ms
WHERE ms.snapshot_at = (
    SELECT MAX(ms2.snapshot_at)
    FROM metric_snapshot ms2
    WHERE ms2.source_video_id = ms.source_video_id
);


DROP VIEW IF EXISTS `v_source_latest_enriched`;

CREATE VIEW `v_source_latest_enriched` AS
SELECT e.*
FROM v_metric_snapshot_enriched e
JOIN v_source_latest_snapshot s ON s.id = e.id;


DROP VIEW IF EXISTS `v_video_latest_rollup`;

CREATE VIEW `v_video_latest_rollup` AS
SELECT
  v.id                      AS video_id,
  v.slug,
  v.title                   AS video_title,
  v.published_at            AS video_published_at,
  v.duration_seconds        AS canonical_length_seconds,

  -- agrégats (somme) sur les derniers snapshots
  SUM(e.views_native_fallback)         AS views_native_sum,
  SUM(e.likes)                         AS likes_sum,
  SUM(e.comments)                      AS comments_sum,
  SUM(e.shares)                        AS shares_sum,
  SUM(e.total_watch_seconds)           AS total_watch_seconds_sum,

  -- par plateforme
  SUM(CASE WHEN e.platform='YOUTUBE'  THEN e.views_native_fallback ELSE 0 END) AS views_yt,
  SUM(CASE WHEN e.platform='FACEBOOK' THEN e.views_native_fallback ELSE 0 END) AS views_fb,
  SUM(CASE WHEN e.platform='INSTAGRAM' THEN e.views_native_fallback ELSE 0 END) AS views_ig,
  SUM(CASE WHEN e.platform='TIKTOK'   THEN e.views_native_fallback ELSE 0 END) AS views_tt,

  -- ratios dérivés
  CASE
    WHEN COALESCE(v.duration_seconds,0) = 0 OR COALESCE(SUM(e.views_native_fallback),0) = 0
      THEN NULL
    ELSE (SUM(e.total_watch_seconds) / SUM(e.views_native_fallback)) / v.duration_seconds
  END AS avg_watch_ratio_est,

  CASE
    WHEN COALESCE(v.duration_seconds,0) = 0
      THEN NULL
    ELSE SUM(e.total_watch_seconds) / v.duration_seconds
  END AS watch_equivalent_sum,

  CASE
    WHEN COALESCE(SUM(e.views_native_fallback),0) = 0
      THEN NULL
    ELSE (SUM(e.likes) + SUM(e.comments) + SUM(e.shares)) /
         SUM(e.views_native_fallback)
  END AS engagement_rate_sum

FROM video v
LEFT JOIN source_video sv
       ON sv.video_id = v.id AND sv.is_active = 1
LEFT JOIN v_source_latest_enriched e
       ON e.source_video_id = sv.id
GROUP BY v.id, v.slug, v.title, v.published_at, v.duration_seconds;
