-- Minimal bootstrap to validate Flyway wiring (no business tables yet).
CREATE TABLE IF NOT EXISTS app_bootstrap (
  id INT PRIMARY KEY,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_bootstrap (id) VALUES (1)
  ON DUPLICATE KEY UPDATE created_at = CURRENT_TIMESTAMP;

-- Next migrations will introduce domain tables.

