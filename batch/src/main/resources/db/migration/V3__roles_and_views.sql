-- V3 — rename video_director to video_realisateur; refresh views
SET NAMES utf8mb4;
DROP VIEW IF EXISTS v_video_totals_latest;
DROP VIEW IF EXISTS v_source_metrics_latest;
DROP VIEW IF EXISTS v_source_latest;

-- Create new link table if not exists
CREATE TABLE IF NOT EXISTS video_realisateur (
  video_id BIGINT NOT NULL,
  person_id BIGINT NOT NULL,
  PRIMARY KEY (video_id, person_id),
  CONSTRAINT fk_vr_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
  CONSTRAINT fk_vr_person FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- If old table exists, migrate data then drop it
INSERT IGNORE INTO video_realisateur (video_id, person_id)
  SELECT video_id, person_id FROM video_director;
DROP TABLE IF EXISTS video_director;

-- Recreate views
CREATE OR REPLACE VIEW v_source_latest AS
SELECT ms.source_video_id, MAX(ms.snapshot_at) AS latest_at
FROM metric_snapshot ms
GROUP BY ms.source_video_id;

CREATE OR REPLACE VIEW v_source_metrics_latest AS
SELECT s.id AS source_video_id,
       s.video_id,
       s.platform,
       s.is_teaser,
       s.published_at,
       m.views, m.comments, m.reactions, m.shares, m.saves,
       l.latest_at
FROM source_video s
JOIN v_source_latest l ON l.source_video_id = s.id
JOIN metric_snapshot m ON m.source_video_id = s.id AND m.snapshot_at = l.latest_at;

CREATE OR REPLACE VIEW v_video_totals_latest AS
SELECT s.video_id,
       SUM(CASE WHEN s.platform <> 'WORDPRESS' THEN COALESCE(m.views,0) ELSE 0 END) AS views_total_no_wp,
       SUM(CASE WHEN s.platform = 'WORDPRESS' THEN COALESCE(m.views,0) ELSE 0 END)   AS views_wp,
       SUM(COALESCE(m.comments,0)) AS comments_total,
       SUM(COALESCE(m.reactions,0)) AS reactions_total,
       SUM(COALESCE(m.shares,0)) AS shares_total,
       SUM(COALESCE(m.saves,0)) AS saves_total
FROM source_video s
LEFT JOIN v_source_metrics_latest m ON m.source_video_id = s.id
GROUP BY s.video_id;
