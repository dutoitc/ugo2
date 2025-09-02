SET NAMES utf8mb4;
SET time_zone = '+00:00';
--CREATE DATABASE IF NOT EXISTS `ugo2_prod` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--USE `ugo2_prod`;

CREATE TABLE person (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  full_name VARCHAR(160) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE location (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  latitude DECIMAL(10,7),
  longitude DECIMAL(10,7),
  city VARCHAR(120),
  region VARCHAR(120),
  country CHAR(2) DEFAULT 'CH'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE video (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ext_uid VARCHAR(64) NULL UNIQUE,
  canonical_title VARCHAR(255),
  canonical_description TEXT,
  official_published_at DATETIME,
  location_id BIGINT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_video_loc FOREIGN KEY (location_id) REFERENCES location(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE video_presentateur (
  video_id BIGINT NOT NULL,
  person_id BIGINT NOT NULL,
  PRIMARY KEY(video_id, person_id),
  CONSTRAINT fk_vp_vid FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
  CONSTRAINT fk_vp_per FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE video_realisateur (
  video_id BIGINT NOT NULL,
  person_id BIGINT NOT NULL,
  PRIMARY KEY(video_id, person_id),
  CONSTRAINT fk_vr_vid FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
  CONSTRAINT fk_vr_per FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  label VARCHAR(80) UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE video_tag (
  video_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY(video_id, tag_id),
  CONSTRAINT fk_vt_vid FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE,
  CONSTRAINT fk_vt_tag FOREIGN KEY (tag_id) REFERENCES tag(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE source_video (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform VARCHAR(16) NOT NULL,
  platform_source_id VARCHAR(64) NOT NULL,
  video_id BIGINT NULL,
  is_teaser TINYINT(1) NOT NULL DEFAULT 0,
  locked TINYINT(1) NOT NULL DEFAULT 0,
  title VARCHAR(255),
  description TEXT,
  permalink_url VARCHAR(512),
  media_type VARCHAR(20),
  duration_seconds INT,
  published_at DATETIME,
  etag VARCHAR(128),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_src (platform, platform_source_id),
  KEY idx_src_vid (video_id),
  KEY idx_src_published (published_at),
  CONSTRAINT fk_src_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE metric_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_video_id BIGINT NOT NULL,
  snapshot_at DATETIME NOT NULL,
  views_3s INT NOT NULL DEFAULT 0,
  views_platform_raw INT DEFAULT 0,
  comments INT DEFAULT 0,
  shares INT DEFAULT 0,
  reactions INT DEFAULT 0,
  saves INT DEFAULT 0,
  UNIQUE KEY uq_snap (source_video_id, snapshot_at),
  KEY idx_snap_src (source_video_id),
  CONSTRAINT fk_snap_src FOREIGN KEY (source_video_id) REFERENCES source_video(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reconcile_override (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_video_id BIGINT NOT NULL,
  action VARCHAR(16) NOT NULL,
  target_video_id BIGINT NULL,
  created_by VARCHAR(128) DEFAULT 'api',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  KEY idx_ro_source (source_video_id),
  CONSTRAINT fk_ro_source FOREIGN KEY (source_video_id) REFERENCES source_video(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE api_idempotency (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  idem_key VARCHAR(64) NOT NULL,
  route VARCHAR(120) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  response_code INT NOT NULL,
  response_body MEDIUMTEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_idem (idem_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
