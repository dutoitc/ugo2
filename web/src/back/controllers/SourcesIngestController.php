<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Lib\Http;
use PDO;

/**
 * Ingestion des sources (table: source_video).
 * Schéma selon web/sql/001_schema.sql:
 *   - platform VARCHAR(16) NOT NULL
 *   - platform_source_id VARCHAR(64) NOT NULL
 *   - title VARCHAR(255) NULL
 *   - description TEXT NULL
 *   - permalink_url VARCHAR(512) NULL
 *   - media_type VARCHAR(20) NULL
 *   - duration_seconds INT NULL
 *   - published_at DATETIME NULL
 *   - etag VARCHAR(128) NULL
 *   - UNIQUE KEY uq_src (platform, platform_source_id)
 */
final class SourcesIngestController
{
    public function __construct(private Db $db, private Auth $auth) {}

    /**
     * POST /api/v1/sources:filterMissing
     * Entrées tolérées:
     *   { "platform":"YOUTUBE", "ids":[...] }
     *   { "platform":"YOUTUBE", "platformIds":[...] }
     *   { "sources":[{"platform":"YOUTUBE","platformId":"..."}] }
     * Retourne les IDs absents de source_video.
     */
    public function filterMissing(): void
    {
        $in = Http::readJson();

        $platform = strtoupper((string)($in['platform'] ?? 'YOUTUBE'));
        $ids = [];

        if (!empty($in['ids']) && is_array($in['ids'])) {
            $ids = array_values(array_filter(array_map('strval', $in['ids'])));
        } elseif (!empty($in['platformIds']) && is_array($in['platformIds'])) {
            $ids = array_values(array_filter(array_map('strval', $in['platformIds'])));
        } elseif (!empty($in['sources']) && is_array($in['sources'])) {
            foreach ($in['sources'] as $s) {
                if (!is_array($s)) continue;
                $p = strtoupper((string)($s['platform'] ?? $platform));
                if ($p !== $platform) continue;
                $pid = $s['platformId'] ?? $s['platform_source_id'] ?? $s['id'] ?? null;
                if ($pid !== null && $pid !== '') $ids[] = (string)$pid;
            }
            $ids = array_values(array_unique($ids));
        }

        if (!$ids) {
            Http::json(['error'=>'missing_ids','message'=>'Aucun identifiant fourni'], 400);
            return;
        }

        $pdo = $this->db->pdo();
        $ph = implode(',', array_fill(0, count($ids), '?'));
        $sql = "SELECT platform_source_id FROM source_video WHERE platform = ? AND platform_source_id IN ($ph)";
        $stmt = $pdo->prepare($sql);
        $stmt->execute(array_merge([$platform], $ids));
        $existing = $stmt->fetchAll(PDO::FETCH_COLUMN, 0) ?: [];
        $existingMap = array_flip($existing);

        $missing = array_values(array_filter($ids, fn($id) => !isset($existingMap[$id])));

        Http::json([
            'platform'       => $platform,
            'requestedCount' => count($ids),
            'existingCount'  => count($existing),
            'missingCount'   => count($missing),
            'missing'        => $missing,
        ], 200);
    }

    /**
     * POST /api/v1/sources:batchUpsert
     * Entrée:
     *   { "sources":[ { platform, platformId, title?, description?, url?, media_type?, duration_seconds?, publishedAt?, etag? }, ... ] }
     * Upsert sur (platform, platform_source_id).
     */
    public function batchUpsert(): void
    {
        $in = Http::readJson();

        $items = $in['sources'] ?? null;
        if (!is_array($items) || !$items) {
            Http::json(['error'=>'invalid_payload','message'=>'Champ sources[] requis'], 400);
            return;
        }

        $pdo = $this->db->pdo();

        $inserted = 0;
        $updated  = 0;
        $skipped  = 0;

        $sql = <<<SQL
INSERT INTO source_video
  (platform, platform_source_id, title, description, permalink_url, media_type, duration_seconds, published_at, etag)
VALUES
  (?, ?, ?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  description = VALUES(description),
  permalink_url = VALUES(permalink_url),
  media_type = VALUES(media_type),
  duration_seconds = VALUES(duration_seconds),
  published_at = VALUES(published_at),
  etag = VALUES(etag)
SQL;

        $stmt = $pdo->prepare($sql);

        foreach ($items as $s) {
            if (!is_array($s)) { $skipped++; continue; }

            $platform   = isset($s['platform']) ? strtoupper((string)$s['platform']) : 'YOUTUBE';
            $platformId = (string)($s['platformId'] ?? $s['platform_source_id'] ?? $s['id'] ?? '');
            if ($platformId === '') { $skipped++; continue; }

            $title        = isset($s['title']) ? (string)$s['title'] : null;
            $description  = isset($s['description']) ? (string)$s['description'] : null;
            $permalinkUrl = isset($s['url']) ? (string)$s['url'] : (isset($s['permalink_url']) ? (string)$s['permalink_url'] : null);
            $mediaType    = isset($s['media_type']) ? (string)$s['media_type'] : null;
            $duration     = isset($s['duration_seconds']) ? (int)$s['duration_seconds'] : null;
            $publishedAt  = isset($s['publishedAt']) ? $this->toMysqlDateTime((string)$s['publishedAt']) : (isset($s['published_at']) ? (string)$s['published_at'] : null);
            $etag         = isset($s['etag']) ? (string)$s['etag'] : null;

            $stmt->execute([$platform, $platformId, $title, $description, $permalinkUrl, $mediaType, $duration, $publishedAt, $etag]);

            $aff = $stmt->rowCount();
            if ($aff >= 2) $updated++;
            else $inserted++;
        }

        Http::json(['ok'=>true,'inserted'=>$inserted,'updated'=>$updated,'skipped'=>$skipped], 200);
    }

    private function toMysqlDateTime(?string $iso8601): ?string
    {
        if (!$iso8601) return null;
        $ts = strtotime($iso8601);
        if ($ts === false) return null;
        return gmdate('Y-m-d H:i:s', $ts);
    }
}
