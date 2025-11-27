/* =======================================================================
   004_reconcile_overrides.sql
   Table pour les corrections manuelles / API reconcile
   ======================================================================= */

SET NAMES utf8mb4;
SET time_zone = '+00:00';

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS `reconcile_override`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `reconcile_override` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `source_video_id` BIGINT UNSIGNED NOT NULL,      -- FK -> source_video.id
  `action`          VARCHAR(32)   NOT NULL,        -- IGNORE | FORCE_LINK | UNLINK | etc.
  `target_video_id` BIGINT UNSIGNED NULL,          -- FK -> video.id (NULL si pas applicable)
  `note`            VARCHAR(255)  NULL,
  `created_by`      VARCHAR(128)  NOT NULL DEFAULT 'api',
  `created_at`      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

  PRIMARY KEY (`id`),
  KEY `idx_ro_source` (`source_video_id`),
  KEY `idx_ro_target` (`target_video_id`),

  CONSTRAINT `fk_ro_source`
    FOREIGN KEY (`source_video_id`) REFERENCES `source_video`(`id`)
    ON UPDATE RESTRICT ON DELETE CASCADE,

  CONSTRAINT `fk_ro_target`
    FOREIGN KEY (`target_video_id`) REFERENCES `video`(`id`)
    ON UPDATE RESTRICT ON DELETE SET NULL
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
