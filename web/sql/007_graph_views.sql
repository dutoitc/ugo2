CREATE OR REPLACE VIEW v_video_metrics_resolved AS
SELECT
    sv.video_id                         AS video_id,
    sv.platform                         AS source,
    ms.snapshot_at                      AS captured_at,   -- alias pratique pour la suite

    /* vues résolues : native si dispo, sinon reach (cas FB Reels etc.) */
    COALESCE(ms.views_native, ms.reach) AS views,

    ms.likes                            AS likes,
    ms.comments                         AS comments,
    ms.shares                           AS shares,
    ms.total_watch_seconds              AS total_watch_seconds
FROM metric_snapshot ms
JOIN source_video sv
  ON sv.id = ms.source_video_id
WHERE sv.is_active = 1;

CREATE INDEX idx_metric_snapshot_sv_time
ON metric_snapshot (source_video_id, snapshot_at);

CREATE INDEX idx_source_video_active
ON source_video (id, is_active);


/* ============================================================
   MV 1 — courbes alignées (active / temps réel)
   ============================================================ */

CREATE TABLE IF NOT EXISTS mv_video_views_aligned (
    video_id        BIGINT NOT NULL,
    source          VARCHAR(16) NOT NULL,
    granularity     ENUM('hour','day') NOT NULL,
    age_bucket      INT NOT NULL,               -- heures ou jours depuis J0
    bucket_ts       DATETIME NOT NULL,
    views_cum       INT NOT NULL,
    views_delta     INT NOT NULL,

    PRIMARY KEY (video_id, source, granularity, age_bucket),
    KEY idx_lookup (video_id, source, granularity, age_bucket),
    KEY idx_bucket (granularity, age_bucket)
) ENGINE=InnoDB;


/* ============================================================
   MV 2 — percentiles (fond gris, recalcul lent)
   ============================================================ */

CREATE TABLE IF NOT EXISTS mv_video_views_percentiles (
    source          VARCHAR(16) NOT NULL,
    granularity     ENUM('hour','day') NOT NULL,
    age_bucket      INT NOT NULL,

    p10_views       INT,
    p25_views       INT,
    p50_views       INT,
    p75_views       INT,
    p90_views       INT,
    count_videos    INT NOT NULL,

    updated_at      DATETIME NOT NULL,

    PRIMARY KEY (source, granularity, age_bucket),
    KEY idx_granularity (granularity, age_bucket)
) ENGINE=InnoDB;




/* ============================================================
   Refresh control (tu l’as déjà, mais on sécurise)
   ============================================================ */

CREATE TABLE IF NOT EXISTS mv_refresh_control (
    name            VARCHAR(64) PRIMARY KEY,
    last_refresh    DATETIME NULL
) ENGINE=InnoDB;