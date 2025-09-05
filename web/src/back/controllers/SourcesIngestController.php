<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Lib\Http;
use PDO;

/**
 * Ingestion des sources (table: source_video).
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
                $pid = $s['platformId'] ?? $s['platform_video_id'] ?? $s['platform_source_id'] ?? $s['id'] ?? null;
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
        $sql = "SELECT platform_video_id FROM source_video WHERE platform = ? AND platform_video_id IN ($ph)";
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
        $inserted = 0; $updated = 0; $skipped = 0;

        // NOTE: upsert sur (platform, platform_video_id) → prévoir un index UNIQUE (voir §2).
        $sql = <<<SQL
    INSERT INTO source_video
      (platform, platform_format, platform_channel_id, platform_video_id,
       title, description, url, etag, published_at, duration_seconds, is_active)
    VALUES
      (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
    ON DUPLICATE KEY UPDATE
      platform_format = VALUES(platform_format),
      platform_channel_id = VALUES(platform_channel_id),
      title = VALUES(title),
      description = VALUES(description),
      url = VALUES(url),
      etag = VALUES(etag),
      published_at = VALUES(published_at),
      duration_seconds = VALUES(duration_seconds),
      is_active = VALUES(is_active)
    SQL;

        $stmt = $pdo->prepare($sql);

        foreach ($items as $s) {
            if (!is_array($s)) { $skipped++; continue; }

            $platform = isset($s['platform']) ? strtoupper((string)$s['platform']) : 'YOUTUBE';

            // tolère plusieurs clés d’entrée pour l’ID vidéo
            $platformVideoId = (string)($s['platform_video_id']
                ?? $s['platformId']
                ?? $s['platform_source_id']
                ?? $s['id']
                ?? '');

            if ($platformVideoId === '') { $skipped++; continue; }

            // format / media-type
            $platformFormat = $this->normalizeFormat(
                $s['platform_format'] ?? null,
                $s['media_type'] ?? null
            );

            $platformChannelId = isset($s['platform_channel_id']) ? (string)$s['platform_channel_id'] : null;

            $title        = isset($s['title']) ? (string)$s['title'] : null;
            $description  = isset($s['description']) ? (string)$s['description'] : null;

            // url / permalink
            $url = isset($s['url']) ? (string)$s['url']
                 : (isset($s['permalink_url']) ? (string)$s['permalink_url'] : null);

            $etag         = isset($s['etag']) ? (string)$s['etag'] : null;
            $duration     = isset($s['duration_seconds']) ? (int)$s['duration_seconds'] : null;

            // published_at / publishedAt (ISO-8601 -> DATETIME)
            $publishedAtIso = $s['published_at'] ?? $s['publishedAt'] ?? null;
            $publishedAt = $this->toMysqlDateTime($publishedAtIso ? (string)$publishedAtIso : null);

            try {
                $stmt->execute([
                    $platform,
                    $platformFormat,
                    $platformChannelId,
                    $platformVideoId,
                    $title,
                    $description,
                    $url,
                    $etag,
                    $publishedAt,
                    $duration
                ]);
            } catch (\PDOException $e) {
                // log utile pour debug
                error_log("[sources:batchUpsert] PDOException: " . $e->getMessage());
                $skipped++; // on continue sur item suivant
                continue;
            }

            $aff = $stmt->rowCount();
            if ($aff >= 2) $updated++; else $inserted++;
        }

        Http::json(['ok'=>true,'inserted'=>$inserted,'updated'=>$updated,'skipped'=>$skipped], 200);
    }


    private function normalizeFormat(?string $format, ?string $mediaType): string
    {
        $f = strtoupper((string)($format ?? $mediaType ?? 'VIDEO'));
        return in_array($f, ['VIDEO','SHORT','REEL'], true) ? $f : 'VIDEO';
    }

    private function toMysqlDateTime(?string $iso8601): ?string
    {
        if (!$iso8601) return null;
        $ts = strtotime($iso8601);
        if ($ts === false) return null;
        // ta colonne est DATETIME(3) → on peut ignorer les ms
        return gmdate('Y-m-d H:i:s', $ts);
    }

}
