SET NAMES utf8mb4;

-- 1) Clés uniques supplémentaires alignées avec l’export
--   - source_video: ux_source_platform_id (en plus de uq_src)
--   - metric_snapshot: ux_metric_source_ts & uq_metric_snapshot (en plus de uq_snap)

-- MariaDB 10.6 supporte CREATE (UNIQUE) INDEX IF NOT EXISTS
CREATE UNIQUE INDEX IF NOT EXISTS ux_source_platform_id
  ON source_video (platform, platform_source_id);

CREATE UNIQUE INDEX IF NOT EXISTS ux_metric_source_ts
  ON metric_snapshot (source_video_id, snapshot_at);

CREATE UNIQUE INDEX IF NOT EXISTS uq_metric_snapshot
  ON metric_snapshot (source_video_id, snapshot_at);

-- 2) (Optionnel) Recréation des vues avec une définition équivalente à l’export,
--    sans DEFINER (évite les soucis de droits) et avec ALGORITHM=UNDEFINED.

DROP VIEW IF EXISTS v_latest_snapshot;
CREATE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW v_latest_snapshot AS
SELECT ms.*
FROM metric_snapshot ms
JOIN (
  SELECT source_video_id, MAX(snapshot_at) AS last_snap
  FROM metric_snapshot
  GROUP BY source_video_id
) m ON m.source_video_id = ms.source_video_id
   AND m.last_snap = ms.snapshot_at;

DROP VIEW IF EXISTS v_video_totals;
CREATE ALGORITHM=UNDEFINED SQL SECURITY INVOKER VIEW v_video_totals AS
SELECT
  v.id AS video_id,
  v.canonical_title,
  v.official_published_at,
  COALESCE(
    SUM(CASE WHEN sv.platform <> 'WORDPRESS' THEN vs.views_3s ELSE 0 END),
    0
  ) AS total_views_3s_excl_wp
FROM video v
LEFT JOIN source_video     sv ON sv.video_id = v.id
LEFT JOIN v_latest_snapshot vs ON vs.source_video_id = sv.id
GROUP BY v.id, v.canonical_title, v.official_published_at;
