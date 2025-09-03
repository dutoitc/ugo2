ALTER TABLE source_video     ADD INDEX idx_source_video_video_id (video_id);
ALTER TABLE source_video     ADD INDEX idx_source_video_platform_pub (platform, published_at);
ALTER TABLE metric_snapshot  ADD INDEX idx_metric_source_last (source_video_id, snapshot_at);
ALTER TABLE video            ADD INDEX idx_video_published (official_published_at);
