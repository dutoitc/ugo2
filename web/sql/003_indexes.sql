/* =======================================================================
   003_indexes.sql
   Index pour UGO2 (optimisation requêtes métriques)
   ======================================================================= */

-- accélère les recherches par vidéo + dernier snapshot
-- (l’ordre DESC est ignoré sur les anciennes versions, mais accepté en syntaxe)
CREATE INDEX `idx_ms_vid_snap` ON `metric_snapshot` (`source_video_id`, `snapshot_at`);

-- utile pour filtres/agrégats par plateforme
CREATE INDEX `idx_sv_video_platform` ON `source_video` (`video_id`, `platform`);
