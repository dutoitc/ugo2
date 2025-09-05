/* 001_schema.sql
 * Schéma UGO2 - révision "metrics v2"
 * Convention: toutes les dates/horaires sont stockées en UTC.
 */
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET FOREIGN_KEY_CHECKS = 0;

-- Drop dans l'ordre des dépendances
DROP TABLE IF EXISTS `metric_snapshot`;
DROP TABLE IF EXISTS `source_video`;
DROP TABLE IF EXISTS `video`;

-- =========================================================
-- Table VIDEO (canon: regroupe des sources multi-plateformes)
-- =========================================================
CREATE TABLE `video` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `slug` VARCHAR(180) NULL,
  `title` VARCHAR(500) NOT NULL,
  `description` TEXT NULL,
  `published_at` DATETIME(3) NULL,              -- UTC (canon)
  `duration_seconds` INT UNSIGNED NULL,         -- longueur "canonique" si connue
  `is_locked` TINYINT(1) NOT NULL DEFAULT 0,    -- protège la réconciliation
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_video_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- Table SOURCE_VIDEO (une source par plateforme/ID)
-- =========================================================
CREATE TABLE `source_video` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `video_id` BIGINT UNSIGNED NULL,                            -- lien optionnel vers VIDEO (peut être NULL avant réconciliation)

  `platform` ENUM('YOUTUBE','FACEBOOK','INSTAGRAM','TIKTOK') NOT NULL,
  `platform_format` ENUM('VIDEO','SHORT','REEL') NULL,        -- type de contenu (YT: VIDEO/SHORT, FB: VIDEO/REEL, ...)

  `platform_channel_id` VARCHAR(190) NULL,
  `platform_video_id`   VARCHAR(190) NOT NULL,                -- identifiant natif (ex: YouTube videoId, Facebook {pageId_postId})

  `title` VARCHAR(500) NULL,
  `description` TEXT NULL,
  `url` VARCHAR(500) NULL,
  `etag` VARCHAR(190) NULL,                                   -- si APIs exposent un etag/version

  `published_at` DATETIME(3) NULL,                            -- UTC selon la plateforme
  `duration_seconds` INT UNSIGNED NULL,                       -- longueur au niveau source

  `is_active` TINYINT(1) NOT NULL DEFAULT 1,

  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_source_platform_vid` (`platform`,`platform_video_id`),
  ADD UNIQUE KEY uq_source_platform_video (platform, platform_video_id),
  KEY `idx_source_video_video_id` (`video_id`),

  CONSTRAINT `fk_source_video__video`
    FOREIGN KEY (`video_id`) REFERENCES `video`(`id`)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================================
-- Table METRIC_SNAPSHOT (métriques brutes par source et par instant)
-- =========================================================
CREATE TABLE `metric_snapshot` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_video_id` BIGINT UNSIGNED NOT NULL,
  `snapshot_at` DATETIME(3) NOT NULL,                          -- UTC

  -- vues natives cross-plateforme (définition propre à chaque plateforme)
  `views_native` BIGINT UNSIGNED NULL,

  -- temps de visionnage
  `avg_watch_seconds` INT UNSIGNED NULL,
  `total_watch_seconds` BIGINT UNSIGNED NULL,
  `video_length_seconds` INT UNSIGNED NULL,                    -- longueur au moment du snapshot (si dispo)

  -- audience
  `reach` BIGINT UNSIGNED NULL,
  `unique_viewers` BIGINT UNSIGNED NULL,

  -- engagement brut
  `likes` BIGINT UNSIGNED NULL,
  `comments` BIGINT UNSIGNED NULL,
  `shares` BIGINT UNSIGNED NULL,

  -- réactions détaillées (surtout FB)
  `reactions_total` BIGINT UNSIGNED NULL,
  `reactions_like`  BIGINT UNSIGNED NULL,
  `reactions_love`  BIGINT UNSIGNED NULL,
  `reactions_wow`   BIGINT UNSIGNED NULL,
  `reactions_haha`  BIGINT UNSIGNED NULL,
  `reactions_sad`   BIGINT UNSIGNED NULL,
  `reactions_angry` BIGINT UNSIGNED NULL,

  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ms_source_time` (`source_video_id`,`snapshot_at`),

  CONSTRAINT `fk_metric_snapshot__source_video`
    FOREIGN KEY (`source_video_id`) REFERENCES `source_video`(`id`)
    ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

