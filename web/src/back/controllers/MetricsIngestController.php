<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Lib\Http;
use PDO;

/**
 * Ingestion métriques (table: metric_snapshot)
 * - Supporte 2 schémas:
 *   MODE A (clé logique):
 *     platform, platform_source_id, captured_at, <metrics...>
 *   MODE B (clé via FK):
 *     source_video_id, captured_at, <metrics...>
 *     -> on résout source_video_id via (platform, platform_source_id) fournis dans le payload
 *
 * Recommandé: contrainte UNIQUE sur (platform,platform_source_id,captured_at) en mode A
 *             ou (source_video_id,captured_at) en mode B pour permettre l'UPSERT propre.
 */
final class MetricsIngestController
{
    public function __construct(private Db $db, private Auth $auth) {}

    /** POST /api/v1/metrics:batchUpsert */
    public function batchUpsert(): void
    {
        $in = Http::readJson();

        $items = $in['snapshots'] ?? null;
        if (!is_array($items) || !$items) {
            Http::json(['error'=>'invalid_payload','message'=>'Champ snapshots[] requis'], 400);
            return;
        }

        $pdo  = $this->db->pdo();
        $cols = $this->detectMetricSnapshotColumns($pdo);   // colonnes existantes de metric_snapshot
        $srcC = $this->detectSourceVideoColumns($pdo);       // colonnes importantes de source_video (pour FK)

        // Déterminer le mode A vs B
        $modeA = isset($cols['platform'], $cols['platform_source_id'], $cols['captured_at']);
        $modeB = isset($cols['source_video_id'], $cols['captured_at']);

        if (!$modeA && !$modeB) {
            Http::json([
                'error'   => 'schema_not_supported',
                'message' => 'metric_snapshot nécessite (platform, platform_source_id, captured_at) ou (source_video_id, captured_at).'
            ], 500);
            return;
        }

        $inserted = 0;
        $updated  = 0;
        $skipped  = 0;

        foreach ($items as $s) {
            if (!is_array($s)) { $skipped++; continue; }

            // Normalisation entrées
            $platform   = strtoupper((string)($s['platform'] ?? 'YOUTUBE'));
            $platformId = (string)($s['platformId'] ?? $s['platform_source_id'] ?? $s['id'] ?? '');
            $capturedIn = (string)($s['capturedAt'] ?? $s['captured_at'] ?? '');

            if ($capturedIn === '') { $skipped++; continue; }
            $capturedAt = $this->toMysqlDateTime($capturedIn);
            if ($capturedAt === null) { $skipped++; continue; }

            // Prépare la ligne à insérer
            $row = [];

            if ($modeA) {
                if ($platformId === '') { $skipped++; continue; }
                $row[$cols['platform']]           = $platform;
                $row[$cols['platform_source_id']] = $platformId;
                $row[$cols['captured_at']]        = $capturedAt;
            } else { // MODE B
                // On doit résoudre source_video_id
                $srcId = $this->resolveSourceVideoId($pdo, $srcC, $platform, $platformId);
                if ($srcId === null) {
                    // Pas de source correspondante -> on saute (ou on pourrait upsert la source ici si souhaité)
                    $skipped++;
                    continue;
                }
                $row[$cols['source_video_id']] = $srcId;
                $row[$cols['captured_at']]     = $capturedAt;
            }

            // Champs métriques optionnels — ajoutés seulement s’ils existent en DB
            $this->putIfExistsInt($row, $cols, 'views',               $s, ['views','viewCount','view_count']);
            $this->putIfExistsInt($row, $cols, 'likes',               $s, ['likes','likeCount','like_count']);
            $this->putIfExistsInt($row, $cols, 'comments',            $s, ['comments','commentCount','comment_count']);
            $this->putIfExistsInt($row, $cols, 'shares',              $s, ['shares','shareCount','share_count']);
            $this->putIfExistsInt($row, $cols, 'reactions',           $s, ['reactions','reactionCount','reaction_count']);
            $this->putIfExistsInt($row, $cols, 'dislikes',            $s, ['dislikes','dislikeCount','dislike_count']);
            $this->putIfExistsInt($row, $cols, 'favorites',           $s, ['favorites','favoriteCount','favorite_count']);
            $this->putIfExistsInt($row, $cols, 'watch_time_seconds',  $s, ['watch_time_seconds','watchTime','watch_time']);
            $this->putIfExistsInt($row, $cols, 'engagements',         $s, ['engagements','engagement_count']); // si présent dans ton schéma

            // Construction SQL dynamique + ON DUPLICATE
            $colNames = array_keys($row);
            $placeH   = implode(',', array_fill(0, count($colNames), '?'));

            $updates = [];
            foreach ($colNames as $cn) {
                if ($modeA) {
                    if ($cn === $cols['platform'] || $cn === $cols['platform_source_id'] || $cn === $cols['captured_at']) continue;
                } else {
                    if ($cn === $cols['source_video_id'] || $cn === $cols['captured_at']) continue;
                }
                $updates[] = "$cn = VALUES($cn)";
            }
            $onDup = $updates ? (' ON DUPLICATE KEY UPDATE ' . implode(', ', $updates)) : '';

            $sql  = 'INSERT INTO metric_snapshot (' . implode(',', $colNames) . ') VALUES (' . $placeH . ')' . $onDup;
            $stmt = $pdo->prepare($sql);
            $stmt->execute(array_values($row));

            $aff = $stmt->rowCount();
            if ($aff >= 2) $updated++;
            else $inserted++;
        }

        Http::json(['ok'=>true,'inserted'=>$inserted,'updated'=>$updated,'skipped'=>$skipped], 200);
    }

    // ------------------------- Helpers -------------------------

    /** Retourne le mapping des colonnes existantes de metric_snapshot (min. ce qui est utile) */
    private function detectMetricSnapshotColumns(PDO $pdo): array
    {
        $existing = [];
        foreach ($pdo->query("SHOW COLUMNS FROM metric_snapshot") as $row) {
            $existing[strtolower((string)$row['Field'])] = (string)$row['Field'];
        }

        $get = function(array $names) use ($existing): ?string {
            foreach ($names as $n) {
                $k = strtolower($n);
                if (isset($existing[$k])) return $existing[$k];
            }
            return null;
        };

        return [
            // clés possibles
            'platform'           => $get(['platform','provider','source_platform']),
            'platform_source_id' => $get(['platform_source_id','platformId','platform_id','external_id','source_id','video_id','provider_id','pid','ref_id']),
            'source_video_id'    => $get(['source_video_id','sourceId','src_id','sv_id']),
            'captured_at'        => $get(['captured_at','capturedAt','timestamp','captured_on','ts','snap_ts','snapshot_at','measured_at','collected_at']),
            // métriques connues
            'views'              => $get(['views','view_count','viewCount']),
            'likes'              => $get(['likes','like_count','likeCount']),
            'comments'           => $get(['comments','comment_count','commentCount']),
            'shares'             => $get(['shares','share_count','shareCount']),
            'reactions'          => $get(['reactions','reaction_count','reactionCount']),
            'dislikes'           => $get(['dislikes','dislike_count','dislikeCount']),
            'favorites'          => $get(['favorites','favorite_count','favoriteCount']),
            'watch_time_seconds' => $get(['watch_time_seconds','watch_time','watchTime']),
            'engagements'        => $get(['engagements','engagement_count']),
        ];
    }

    /** Colonnes utiles de source_video pour résoudre un FK si nécessaire */
    private function detectSourceVideoColumns(PDO $pdo): array
    {
        $existing = [];
        foreach ($pdo->query("SHOW COLUMNS FROM source_video") as $row) {
            $existing[strtolower((string)$row['Field'])] = (string)$row['Field'];
        }

        $get = function(array $names) use ($existing): ?string {
            foreach ($names as $n) {
                $k = strtolower($n);
                if (isset($existing[$k])) return $existing[$k];
            }
            return null;
        };

        $idCol       = $get(['id','source_video_id','sv_id']);
        $platformCol = $get(['platform','provider','source_platform']);
        $pidCol      = $get(['platform_source_id','platformId','platform_id','external_id','source_id','video_id','provider_id','pid','ref_id']);

        return [
            'id'                 => $idCol,
            'platform'           => $platformCol,
            'platform_source_id' => $pidCol,
        ];
    }

    /** Résout source_video.id à partir de (platform, platform_source_id) */
    private function resolveSourceVideoId(PDO $pdo, array $srcCols, string $platform, string $platformId): ?int
    {
        if (!$srcCols['id'] || !$srcCols['platform'] || !$srcCols['platform_source_id']) return null;
        if ($platformId === '') return null;

        $sql = "SELECT {$srcCols['id']} FROM source_video WHERE {$srcCols['platform']} = ? AND {$srcCols['platform_source_id']} = ? LIMIT 1";
        $st  = $pdo->prepare($sql);
        $st->execute([$platform, $platformId]);
        $val = $st->fetchColumn();
        return ($val === false) ? null : (int)$val;
    }

    private function toMysqlDateTime(?string $iso8601): ?string
    {
        if (!$iso8601) return null;
        $ts = strtotime($iso8601);
        if ($ts === false) return null;
        return gmdate('Y-m-d H:i:s', $ts);
    }

    /** Affecte un entier si la colonne existe et la valeur est fournie. */
    private function putIfExistsInt(array &$row, array $cols, string $logical, array $src, array $aliases): void
    {
        if (!isset($cols[$logical]) || !$cols[$logical]) return;
        foreach ($aliases as $k) {
            if (array_key_exists($k, $src) && $src[$k] !== null && $src[$k] !== '') {
                $row[$cols[$logical]] = (int)$src[$k];
                return;
            }
        }
    }
}
