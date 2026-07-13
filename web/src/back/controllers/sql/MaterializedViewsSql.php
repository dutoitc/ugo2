<?php
declare(strict_types=1);

namespace Web\Controllers\Sql;

final class MaterializedViewsSql
{
    public const WINDOW_MONTHS = 6;
    public const HOUR_HORIZON = 720;

    public const ENSURE_TABLES = <<<SQL

    CREATE TABLE IF NOT EXISTS mv_video_views_aligned_hour_raw (
        video_id BIGINT UNSIGNED NOT NULL,
        source VARCHAR(20) NOT NULL,
        age_bucket INT NOT NULL,
        bucket_ts DATETIME NOT NULL,
        views_cum BIGINT,
        PRIMARY KEY (video_id, source, age_bucket)
    ) ENGINE=InnoDB;

    CREATE TABLE IF NOT EXISTS mv_video_views_aligned_dense (
        video_id BIGINT UNSIGNED NOT NULL,
        source VARCHAR(20) NOT NULL,
        granularity VARCHAR(10) NOT NULL,
        age_bucket INT NOT NULL,
        bucket_ts DATETIME NOT NULL,
        views_cum DOUBLE,
        views_delta DOUBLE NULL,
        PRIMARY KEY (video_id, source, age_bucket)
    ) ENGINE=InnoDB;

    CREATE TABLE IF NOT EXISTS mv_video_views_percentiles (
        source VARCHAR(20) NOT NULL,
        granularity VARCHAR(10) NOT NULL,
        age_bucket INT NOT NULL,

        p10_views DOUBLE,
        p25_views DOUBLE,
        p50_views DOUBLE,
        p75_views DOUBLE,
        p90_views DOUBLE,

        count_videos INT,
        updated_at DATETIME,

        PRIMARY KEY (source, granularity, age_bucket)
    ) ENGINE=InnoDB;

    SQL;


    public const ENSURE_INDEXES = <<<SQL

    CREATE INDEX IF NOT EXISTS idx_raw_bucket
    ON mv_video_views_aligned_hour_raw (source, age_bucket);

    CREATE INDEX IF NOT EXISTS idx_dense_bucket
    ON mv_video_views_aligned_dense (source, age_bucket);

    CREATE INDEX IF NOT EXISTS idx_pct_lookup
    ON mv_video_views_percentiles (source, granularity, age_bucket);

    CREATE INDEX IF NOT EXISTS idx_dense_percentile
    ON mv_video_views_aligned_dense (source, granularity, age_bucket, views_cum);

    SQL;


    // ---------------------------------------------------------------------
    // 1) RAW
    // ---------------------------------------------------------------------
    public const REFRESH_ALIGNED_HOUR_RAW = <<<SQL
REPLACE INTO mv_video_views_aligned_hour_raw
SELECT
    sv.video_id,
    sv.platform AS source,
    TIMESTAMPDIFF(HOUR, sv.published_at, ms.snapshot_at) AS age_bucket,
    DATE_ADD(sv.published_at,
        INTERVAL TIMESTAMPDIFF(HOUR, sv.published_at, ms.snapshot_at) HOUR
    ) AS bucket_ts,
    MAX(ms.views_native) AS views_cum
FROM metric_snapshot ms
JOIN source_video sv ON sv.id = ms.source_video_id
WHERE
    sv.is_active = 1
    AND sv.published_at IS NOT NULL
    AND sv.published_at >= NOW() - INTERVAL 6 MONTH
    AND ms.snapshot_at >= sv.published_at
    AND ms.snapshot_at >= NOW() - INTERVAL 6 MONTH
    AND ms.views_native IS NOT NULL
GROUP BY
    sv.video_id, sv.platform, age_bucket;
SQL;

    // ---------------------------------------------------------------------
    // 2) DENSE (interpolation)
    // ---------------------------------------------------------------------
    public const REFRESH_ALIGNED_HOUR_DENSE = <<<SQL
REPLACE INTO mv_video_views_aligned_dense
WITH RECURSIVE hours AS (
    SELECT 0 AS h
    UNION ALL
    SELECT h + 1 FROM hours WHERE h < 720
),
base AS (
    SELECT
        sv.video_id,
        sv.platform AS source,
        sv.published_at AS t0
    FROM source_video sv
    WHERE
        sv.is_active = 1
        AND sv.published_at IS NOT NULL
        AND sv.published_at >= NOW() - INTERVAL 6 MONTH
),
grid AS (
    SELECT
        b.video_id,
        b.source,
        'hour' AS granularity,
        h.h AS age_bucket,
        DATE_ADD(b.t0, INTERVAL h.h HOUR) AS bucket_ts
    FROM base b
    JOIN hours h
),
joined AS (
    SELECT
        g.*,
        r.views_cum AS v_exact
    FROM grid g
    LEFT JOIN mv_video_views_aligned_hour_raw r
        ON r.video_id = g.video_id
       AND r.source = g.source
       AND r.age_bucket = g.age_bucket
),
marks AS (
    SELECT
        j.*,
        MAX(CASE WHEN j.v_exact IS NOT NULL THEN j.age_bucket END)
            OVER (PARTITION BY j.video_id, j.source ORDER BY j.age_bucket) AS prev_age,

        MIN(CASE WHEN j.v_exact IS NOT NULL THEN j.age_bucket END)
            OVER (PARTITION BY j.video_id, j.source ORDER BY j.age_bucket DESC) AS next_age
    FROM joined j
)
SELECT
    m.video_id,
    m.source,
    m.granularity,
    m.age_bucket,
    m.bucket_ts,

    CASE
        WHEN m.v_exact IS NOT NULL THEN m.v_exact
        WHEN m.prev_age IS NULL THEN 0
        WHEN m.next_age IS NULL THEN rp.views_cum
        WHEN m.prev_age = m.next_age THEN rp.views_cum
        ELSE
            rp.views_cum +
            (
                (m.age_bucket - m.prev_age) * 1.0 /
                NULLIF((m.next_age - m.prev_age),0)
            )
            * (rn.views_cum - rp.views_cum)
    END AS views_cum,

    NULL AS views_delta
FROM marks m
LEFT JOIN mv_video_views_aligned_hour_raw rp
    ON rp.video_id = m.video_id
   AND rp.source = m.source
   AND rp.age_bucket = m.prev_age
LEFT JOIN mv_video_views_aligned_hour_raw rn
    ON rn.video_id = m.video_id
   AND rn.source = m.source
   AND rn.age_bucket = m.next_age;
SQL;

    // ---------------------------------------------------------------------
    // 3) PERCENTILES
    // ---------------------------------------------------------------------
    public const REFRESH_PERCENTILES_HOUR = <<<SQL
    REPLACE INTO mv_video_views_percentiles
    SELECT
        source,
        granularity,
        age_bucket,

        MAX(CASE WHEN rn = r10 THEN views_cum END) AS p10_views,
        MAX(CASE WHEN rn = r25 THEN views_cum END) AS p25_views,
        MAX(CASE WHEN rn = r50 THEN views_cum END) AS p50_views,
        MAX(CASE WHEN rn = r75 THEN views_cum END) AS p75_views,
        MAX(CASE WHEN rn = r90 THEN views_cum END) AS p90_views,

        cnt AS count_videos,
        NOW() AS updated_at

    FROM (
        SELECT
            d.source,
            d.granularity,
            d.age_bucket,
            d.views_cum,

            ROW_NUMBER() OVER (
                PARTITION BY d.source, d.granularity, d.age_bucket
                ORDER BY d.views_cum
            ) AS rn,

            COUNT(*) OVER (
                PARTITION BY d.source, d.granularity, d.age_bucket
            ) AS cnt,

            CEIL(COUNT(*) OVER (PARTITION BY d.source,d.granularity,d.age_bucket)*0.10) AS r10,
            CEIL(COUNT(*) OVER (PARTITION BY d.source,d.granularity,d.age_bucket)*0.25) AS r25,
            CEIL(COUNT(*) OVER (PARTITION BY d.source,d.granularity,d.age_bucket)*0.50) AS r50,
            CEIL(COUNT(*) OVER (PARTITION BY d.source,d.granularity,d.age_bucket)*0.75) AS r75,
            CEIL(COUNT(*) OVER (PARTITION BY d.source,d.granularity,d.age_bucket)*0.90) AS r90

        FROM mv_video_views_aligned_dense d
        WHERE d.granularity = 'hour'
    ) x
    GROUP BY source, granularity, age_bucket, cnt;
    SQL;

}
