-- V5 — ingest checkpoints (discovery resume + last run)
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS ingest_checkpoint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  platform VARCHAR(16) NOT NULL,
  scope VARCHAR(32)  NOT NULL,
  cursor VARCHAR(1024) DEFAULT NULL,
  since DATETIME DEFAULT NULL,
  last_run_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  extra TEXT DEFAULT NULL,
  UNIQUE KEY uq_platform_scope (platform, scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
