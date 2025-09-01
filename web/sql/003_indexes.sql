USE `ugo2_prod`;

CREATE INDEX idx_video_official_published ON video(official_published_at);
CREATE INDEX idx_person_name ON person(full_name);
CREATE INDEX idx_sv_platform ON source_video(platform);
CREATE INDEX idx_sv_perm ON source_video(permalink_url(120));
