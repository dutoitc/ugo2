CREATE OR REPLACE VIEW v_latest_snapshot AS
SELECT ms.*
FROM metric_snapshot ms
JOIN (
  SELECT source_video_id, MAX(snapshot_at) AS last_snap
  FROM metric_snapshot
  GROUP BY source_video_id
) m ON m.source_video_id=ms.source_video_id AND m.last_snap=ms.snapshot_at;

CREATE OR REPLACE VIEW v_video_totals AS
SELECT
  v.id AS video_id,
  v.canonical_title,
  v.official_published_at,
  COALESCE(SUM(CASE WHEN sv.platform <> 'WORDPRESS' THEN vs.views_3s ELSE 0 END),0) AS total_views_3s_excl_wp
FROM video v
LEFT JOIN source_video sv ON sv.video_id = v.id
LEFT JOIN v_latest_snapshot vs ON vs.source_video_id = sv.id
GROUP BY v.id, v.canonical_title, v.official_published_at;
