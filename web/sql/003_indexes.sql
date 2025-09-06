-- VIDEO
CREATE INDEX `idx_video_published_at` ON `video` (`published_at`);
CREATE INDEX `idx_video_locked`       ON `video` (`is_locked`);

-- SOURCE_VIDEO
CREATE INDEX `idx_source_platform_fmt` ON `source_video` (`platform`,`platform_format`);
CREATE INDEX `idx_source_published_at` ON `source_video` (`published_at`);
CREATE INDEX `idx_source_active`       ON `source_video` (`is_active`);

-- METRIC_SNAPSHOT
CREATE INDEX `idx_ms_source_time`      ON `metric_snapshot` (`source_video_id`,`snapshot_at`);
CREATE INDEX `idx_ms_snapshot_at`      ON `metric_snapshot` (`snapshot_at`);
CREATE INDEX `idx_ms_views_native`     ON `metric_snapshot` (`views_native`);

-- accélère les recherches par vidéo + dernier snapshot
CREATE INDEX IF NOT EXISTS idx_ms_vid_snap ON metric_snapshot (source_video_id, snapshot_at DESC);

-- utile pour filtres/agrèges par plateforme
CREATE INDEX IF NOT EXISTS idx_sv_video_platform ON source_video (video_id, platform);