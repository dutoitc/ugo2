-- 005_hardening.sql
-- Durcissement du schéma sans procédures stockées.
-- Prérequis: 001_schema.sql, 002_views.sql, 003_indexes.sql, 004_migrate_to_export.sql

SET NAMES utf8mb4;

-- =========================================================
-- 1) Colonnes UNSIGNED pour éviter les valeurs négatives
--    (plus fiable que CHECK en prod et sans procédures)
--    Schéma des métriques: voir 001_schema.sql et export.
--    Colonnes cibles: views_3s, views_platform_raw, comments, shares, reactions, saves
-- =========================================================
ALTER TABLE metric_snapshot
  MODIFY views_3s           INT UNSIGNED NOT NULL DEFAULT 0,
  MODIFY views_platform_raw INT UNSIGNED          DEFAULT 0,
  MODIFY comments           INT UNSIGNED          DEFAULT 0,
  MODIFY shares             INT UNSIGNED          DEFAULT 0,
  MODIFY reactions          INT UNSIGNED          DEFAULT 0,
  MODIFY saves              INT UNSIGNED          DEFAULT 0;

-- Bonus: durée vidéo non négative
ALTER TABLE source_video
  MODIFY duration_seconds INT UNSIGNED NULL;

-- =========================================================
-- 2) Rappels d’index utiles (idempotents)
--    - certains sont déjà posés par 003 et/ou 004, ici on sécurise
-- =========================================================
CREATE INDEX IF NOT EXISTS idx_video_official_published ON video(official_published_at);
CREATE INDEX IF NOT EXISTS idx_person_name ON person(full_name);
CREATE INDEX IF NOT EXISTS idx_sv_platform ON source_video(platform);
CREATE INDEX IF NOT EXISTS idx_sv_perm ON source_video(permalink_url(120));

-- (004 a déjà créé les uniques pour coller à l'export)
--   source_video:  ux_source_platform_id (platform, platform_source_id)
--   metric_snapshot: ux_metric_source_ts / uq_metric_snapshot (source_video_id, snapshot_at)
-- Référence export: v10xu_ugo2.sql.
-- (Pas besoin de les re-déclarer ici si 004 a été appliqué.)

-- =========================================================
-- 3) Vues en SQL SECURITY INVOKER (pas de DEFINER)
--    (re-définition idempotente et propre)
--    Référence initiale: 002_views.sql
--    Référence export: v10xu_ugo2.sql
-- =========================================================
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
