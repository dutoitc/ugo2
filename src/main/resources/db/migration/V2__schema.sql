-- V2 — Domain schema for UGO2 (MariaDB)
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS person (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  display_name VARCHAR(255) NOT NULL,
  type VARCHAR(32) DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_person_name (display_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS location (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  label VARCHAR(255) DEFAULT NULL,
  latitude DECIMAL(9,6) NOT NULL,
  longitude DECIMAL(9,6) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_location_latlon (latitude, longitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  canonical_title VARCHAR(500) NOT NULL,
  canonical_description TEXT NULL,
  official_published_at DATETIME NULL,
  location_id BIGINT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_video_location FOREIGN KEY (location_id) REFERENCES location(id)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  UNIQUE KEY uq_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_tag (
  video_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (video_id, tag_id),
  CONSTRAINT fk_vt_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
  CONSTRAINT fk_vt_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_presenter (
  video_id BIGINT NOT NULL,
  person_id BIGINT NOT NULL,
  PRIMARY KEY (video_id, person_id),
  CONSTRAINT fk_vp_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
  CONSTRAINT fk_vp_person FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS video_director (
  video_id BIGINT NOT NULL,
  person_id BIGINT NOT NULL,
  PRIMARY KEY (video_id, person_id),
  CONSTRAINT fk_vd_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
  CONSTRAINT fk_vd_person FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS source_video (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  video_id BIGINT NULL,
  platform VARCHAR(16) NOT NULL,
  platform_source_id VARCHAR(191) NOT NULL,
  permalink_url VARCHAR(1000) NULL,
  title VARCHAR(500) NULL,
  description TEXT NULL,
  media_type VARCHAR(16) DEFAULT 'VIDEO',
  is_teaser TINYINT(1) DEFAULT 0,
  published_at DATETIME NOT NULL,
  duration_seconds INT DEFAULT NULL,
  etag VARCHAR(255) NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_platform_source (platform, platform_source_id),
  KEY idx_source_published (platform, published_at),
  KEY idx_source_video (video_id),
  CONSTRAINT fk_source_video FOREIGN KEY (video_id) REFERENCES video(id)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS metric_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_video_id BIGINT NOT NULL,
  snapshot_at DATETIME NOT NULL,
  views BIGINT DEFAULT 0,
  comments BIGINT DEFAULT 0,
  reactions BIGINT DEFAULT 0,
  shares BIGINT DEFAULT 0,
  saves BIGINT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ms_source_time (source_video_id, snapshot_at),
  CONSTRAINT fk_ms_source FOREIGN KEY (source_video_id) REFERENCES source_video(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE OR REPLACE VIEW v_source_latest AS
SELECT ms.source_video_id,
       MAX(ms.snapshot_at) AS latest_at
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

CREATE INDEX IF NOT EXISTS idx_video_official_published_at ON video(official_published_at);
