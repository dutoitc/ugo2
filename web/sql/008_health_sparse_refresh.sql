/* UGO2 - état de santé, batch et refresh différé */
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS platform_health (
  platform VARCHAR(20) NOT NULL,
  last_success_at DATETIME(3) NULL,
  last_snapshot_at DATETIME(3) NULL,
  last_error_at DATETIME(3) NULL,
  last_error VARCHAR(1000) NULL,
  last_duration_ms BIGINT UNSIGNED NULL,
  last_items INT UNSIGNED NULL,
  token_status VARCHAR(24) NOT NULL DEFAULT 'UNKNOWN',
  token_expires_at DATETIME(3) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS batch_run (
  run_id VARCHAR(64) NOT NULL,
  started_at DATETIME(3) NOT NULL,
  finished_at DATETIME(3) NULL,
  duration_ms BIGINT UNSIGNED NULL,
  status VARCHAR(16) NOT NULL,
  items INT UNSIGNED NULL,
  error VARCHAR(1000) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (run_id),
  KEY idx_batch_run_started (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_job_state (
  name VARCHAR(64) NOT NULL,
  dirty_since DATETIME(3) NULL,
  last_started_at DATETIME(3) NULL,
  last_success_at DATETIME(3) NULL,
  last_duration_ms BIGINT UNSIGNED NULL,
  last_status VARCHAR(16) NOT NULL DEFAULT 'NEVER',
  last_error VARCHAR(1000) NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO refresh_job_state(name, last_status)
VALUES ('materialized_views', 'NEVER');
