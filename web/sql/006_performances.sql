CREATE OR REPLACE VIEW v_video_last_snapshot AS
SELECT
  sv.video_id,
  MAX(ms.created_at) AS last_snapshot_at
FROM source_video sv
JOIN metric_snapshot ms ON ms.source_video_id = sv.id
GROUP BY sv.video_id;

CREATE INDEX idx_sv_video ON source_video(video_id);
CREATE INDEX idx_ms_source_created ON metric_snapshot(source_video_id, created_at);

CREATE TABLE mv_video_rollup AS
SELECT
  v.id AS video_id,
  v.slug,
  v.title AS video_title,
  v.published_at AS video_published_at,
  v.duration_seconds AS canonical_length_seconds,

  SUM(e.views_native_fallback) AS views_native_sum,
  SUM(e.likes) AS likes_sum,
  SUM(e.comments) AS comments_sum,
  SUM(e.shares) AS shares_sum,
  SUM(e.total_watch_seconds) AS total_watch_seconds_sum,

  SUM(CASE WHEN e.platform='YOUTUBE' THEN e.views_native_fallback ELSE 0 END) AS views_yt,
  SUM(CASE WHEN e.platform='FACEBOOK' THEN e.views_native_fallback ELSE 0 END) AS views_fb,
  SUM(CASE WHEN e.platform='INSTAGRAM' THEN e.views_native_fallback ELSE 0 END) AS views_ig,
  SUM(CASE WHEN e.platform='TIKTOK' THEN e.views_native_fallback ELSE 0 END) AS views_tt

FROM video v
LEFT JOIN source_video sv ON sv.video_id = v.id AND sv.is_active = 1
LEFT JOIN v_source_latest_enriched e ON e.source_video_id = sv.id
GROUP BY v.id, v.slug, v.title, v.published_at, v.duration_seconds;


ALTER TABLE mv_video_rollup
  ADD PRIMARY KEY (video_id),
  ADD INDEX idx_mv_pub (video_published_at),
  ADD INDEX idx_mv_views_yt (views_yt),
  ADD INDEX idx_mv_views_fb (views_fb),
  ADD INDEX idx_mv_views_ig (views_ig),
  ADD INDEX idx_mv_views_tt (views_tt);

CREATE TABLE mv_refresh_control (
  name VARCHAR(50) PRIMARY KEY,
  last_refresh DATETIME NOT NULL
);

INSERT INTO mv_refresh_control VALUES ('video_rollup', '1970-01-01');

ALTER TABLE mv_video_rollup
  ADD avg_watch_ratio_est DOUBLE,
  ADD watch_equivalent_sum DOUBLE,
  ADD engagement_rate_sum DOUBLE;

