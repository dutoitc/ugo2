<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Auth;
use Web\Db;
use Web\Lib\Http;
use Web\Lib\SensitiveData;
use Web\Controllers\Videos\VideosListRequestFactory;
use Web\Controllers\Videos\VideosRepository;
use Web\Services\MaterializedRefreshService;


final class VideosController
{
    public function __construct(
        private Db $db,
        private ?Auth $auth = null // non utilisé pour l’instant
    ) {}

    private function pdo(): \PDO {
        if (method_exists($this->db, 'pdo')) return $this->db->pdo();
        if (method_exists($this->db, 'getPdo')) return $this->db->getPdo();
        throw new \RuntimeException('Db must expose PDO via pdo() or getPdo()');
    }

    /**
     * GET /api/v1/videos
     * Query params:
     *  - page (>=1, défaut 1)
     *  - size (1..5000, défaut 20)
     *  - q (recherche: titre/slug, LIKE %q%)
     *  - platform (YOUTUBE|FACEBOOK|INSTAGRAM|TIKTOK) [optionnel]
     *  - format (VIDEO|SHORT|REEL) [optionnel, filtre via existence de sources correspondantes]
     *  - from, to (ISO date; filtre sur video_published_at)
     *  - sort: vues totales/par plateforme, publication, engagement, watch eq. ou titre (asc/desc)
     */
    public function list(): void
    {
        $q = VideosListRequestFactory::fromGet($_GET);

        $repo = new VideosRepository($this->pdo());
        $result = $repo->findList($q);

        \Web\Lib\Http::json([
            'page'  => $q->paginator->page,
            'size'  => $q->paginator->size,
            'total' => $result['total'],
            'sum'   => $result['sum'],
            'items' => $result['items'],
        ], 200);
    }



    /**
     * GET /api/v1/video
     * Query params:
     *   - id (id vidéo canonique)
     *   - OU slug=...
     *   - OU (platform=..., platform_video_id=...)
     *
     * Options:
     *   - timeseries=1 (retourne la série temporelle des snapshots par source)
     *   - ts_limit (défaut 50)
     */
    public function get(): void
    {
        $pdo = $this->pdo();

        $id   = isset($_GET['id']) ? (int)$_GET['id'] : null;
        $slug = isset($_GET['slug']) ? trim((string)$_GET['slug']) : null;

        $platform        = isset($_GET['platform']) ? strtoupper(trim((string)$_GET['platform'])) : null;
        $platformVideoId = isset($_GET['platform_video_id']) ? trim((string)$_GET['platform_video_id']) : null;

        $wantTs   = (isset($_GET['timeseries']) && (string)$_GET['timeseries'] === '1');
        $tsLimit  = (int)($_GET['ts_limit'] ?? 50);
        if ($tsLimit < 1) $tsLimit = 50;
        if ($tsLimit > 1000) $tsLimit = 1000;

        // Résolution de la vidéo (id)
        if ($id === null && $slug !== null) {
            $st = $pdo->prepare('SELECT id FROM video WHERE slug = ?');
            $st->execute([$slug]);
            $id = $st->fetchColumn();
            $id = $id !== false ? (int)$id : null;
        }

        if ($id === null && $platform && $platformVideoId) {
            // on retrouve la source puis la vidéo reliée
            $st = $pdo->prepare('SELECT video_id FROM source_video WHERE platform = ? AND platform_video_id = ? LIMIT 1');
            $st->execute([$platform, $platformVideoId]);
            $vid = $st->fetchColumn();
            $id = $vid !== false ? (int)$vid : null;
        }

        if ($id === null) {
            Http::json(['error'=>'bad_request', 'message'=>'Provide id or slug or (platform,platform_video_id)'], 400);
            return;
        }

        // Métadonnées vidéo
        $st = $pdo->prepare('SELECT id, slug, title, description, published_at, duration_seconds, is_locked FROM video WHERE id = ?');
        $st->execute([$id]);
        $video = $st->fetch(\PDO::FETCH_ASSOC);
        if (!$video) {
            Http::json(['error'=>'not_found','id'=>$id], 404);
            return;
        }

        // Rollup (dernier état agrégé pour la vidéo)
        $st = $pdo->prepare('SELECT * FROM mv_video_rollup WHERE video_id = ?');
        $st->execute([$id]);
        $roll = $st->fetch(\PDO::FETCH_ASSOC);

        // Sources liées
        $st = $pdo->prepare('SELECT id, platform, platform_format, platform_channel_id, platform_video_id, title, url, etag, published_at, duration_seconds, is_active FROM source_video WHERE video_id = ? ORDER BY platform, platform_format, id');
        $st->execute([$id]);
        $sources = $st->fetchAll(\PDO::FETCH_ASSOC);

        // Dernières métriques enrichies par source
        $outSources = [];
        foreach ($sources as $s) {
            $sid = (int)$s['id'];
            $latest = $pdo->prepare('SELECT * FROM v_source_latest_enriched WHERE source_video_id = ?');
            $latest->execute([$sid]);
            $m = $latest->fetch(\PDO::FETCH_ASSOC) ?: null;

            $ts = null;
            if ($wantTs) {
                $series = $pdo->prepare('SELECT * FROM v_metric_snapshot_enriched WHERE source_video_id = ? ORDER BY snapshot_at DESC LIMIT ?');
                $series->execute([$sid, $tsLimit]);
                $ts = $series->fetchAll(\PDO::FETCH_ASSOC);
            }

            $outSources[] = [
                'id' => $sid,
                'platform' => $s['platform'],
                'platform_format' => $s['platform_format'],
                'platform_video_id' => $s['platform_video_id'],
                'title' => $s['title'],
                'url'   => $s['url'],
                'published_at' => $s['published_at'],
                'duration_seconds' => $s['duration_seconds'] !== null ? (int)$s['duration_seconds'] : null,
                'is_active' => (int)$s['is_active'] === 1,
                'latest' => $m,
                'timeseries' => $ts,
            ];
        }

        Http::json([
            'video' => [
                'id' => (int)$video['id'],
                'slug' => $video['slug'],
                'title'=> $video['title'],
                'description' => $video['description'],
                'published_at'=> $video['published_at'],
                'duration_seconds' => $video['duration_seconds'] !== null ? (int)$video['duration_seconds'] : null,
                'is_locked' => (int)$video['is_locked'] === 1,
            ],
            'rollup' => $roll,
            'sources'=> $outSources
        ], 200);
    }


    public function duplicates(): void
    {
        $pdo = $this->pdo();

        // Paramètres optionnels
        $windowH       = isset($_GET['window_h']) ? max(1, (int)$_GET['window_h']) : 48;   // fenêtre temporelle en heures
        $durTolSeconds = isset($_GET['duration_tol_s']) ? max(0, (int)$_GET['duration_tol_s']) : 60; // tolérance sur la durée (sec)
        $limit         = isset($_GET['limit']) ? max(1, min(1000, (int)$_GET['limit'])) : 200;
        $offset        = isset($_GET['offset']) ? max(0, (int)$_GET['offset']) : 0;

        // On paramétrise en secondes pour le TIMESTAMPDIFF
        $windowSeconds = $windowH * 3600;

        $sql = "
            SELECT
                ABS(TIMESTAMPDIFF(SECOND, v1.published_at, v2.published_at)) / 3600 AS delta_h,
                v1.id              AS s1_id,
                v1.video_id        AS v1_id,
                v1.title           AS s1_title,
                v1.published_at    AS s1_published_at,
                v1.duration_seconds AS s1_duration_seconds,

                v2.id              AS s2_id,
                v2.video_id        AS v2_id,
                v2.title           AS s2_title,
                v2.published_at    AS s2_published_at,
                v2.duration_seconds AS s2_duration_seconds
            FROM source_video v1
            INNER JOIN source_video v2
                ON v2.id > v1.id
               AND ABS(TIMESTAMPDIFF(SECOND, v1.published_at, v2.published_at)) <= ?
               AND (
                    v1.duration_seconds IS NULL
                    OR v2.duration_seconds IS NULL
                    OR ABS(CONVERT(v1.duration_seconds, SIGNED) - CONVERT(v2.duration_seconds, SIGNED)) < ?
                   )
            WHERE (
                    v1.video_id IS NULL
                    OR v2.video_id IS NULL
                    OR v1.video_id != v2.video_id
                  )
            ORDER BY delta_h ASC, s1_id ASC
            LIMIT ? OFFSET ?;
        ";

        $st = $pdo->prepare($sql);
        $st->execute([$windowSeconds, $durTolSeconds, $limit, $offset]);

        $items = [];
        while ($row = $st->fetch(\PDO::FETCH_ASSOC)) {
            $items[] = [
                'delta_h' => (float)$row['delta_h'],

                'source1' => [
                    'id'               => (int)$row['s1_id'],
                    'video_id'         => (int)$row['v1_id'],
                    'title'            => $row['s1_title'],
                    'published_at'     => $row['s1_published_at'],
                    'duration_seconds' => $row['s1_duration_seconds'] !== null ? (int)$row['s1_duration_seconds'] : null,
                ],
                'source2' => [
                    'id'               => (int)$row['s2_id'],
                    'video_id'         => (int)$row['v2_id'],
                    'title'            => $row['s2_title'],
                    'published_at'     => $row['s2_published_at'],
                    'duration_seconds' => $row['s2_duration_seconds'] !== null ? (int)$row['s2_duration_seconds'] : null,
                ],
            ];
        }

        \Web\Lib\Http::json([
            'params' => [
                'window_h'        => $windowH,
                'duration_tol_s'  => $durTolSeconds,
                'limit'           => $limit,
                'offset'          => $offset,
            ],
            'count' => count($items),
            'items' => $items,
        ], 200);
    }


    /**
     * POST /api/v1/duplicates:resolve
     * Body JSON : { "videoIdToKeep":123, "videoIdToDelete":124, "videoSourceIdToUpdate":5678 }
     */
    public function resolveDuplicate(): void
    {
        $pdo = $this->pdo();

        $input = json_decode(file_get_contents('php://input'), true);
        $keep   = $input['videoIdToKeep']   ?? null;
        $delete = $input['videoIdToDelete'] ?? null;
        $update = $input['videoSourceIdToUpdate'] ?? null;

        if (!$keep || !$delete || !$update) {
            \Web\Lib\Http::json(['error' => 'missing_params'], 400);
            return;
        }

        $pdo->beginTransaction();
        try {
            // 1. update source_video
            $st1 = $pdo->prepare('UPDATE source_video SET video_id = ? WHERE id = ?');
            $st1->execute([$keep, $update]);

            // 2. delete video
            $st2 = $pdo->prepare('DELETE FROM video WHERE id = ?');
            $st2->execute([$delete]);

            $pdo->commit();
            \Web\Lib\Http::json(['ok' => true, 'kept' => $keep, 'deleted' => $delete, 'updated_source' => $update], 200);
        } catch (\Throwable $e) {
            $pdo->rollBack();
            error_log('[duplicates:resolve] '.SensitiveData::throwable($e));
            \Web\Lib\Http::json(['error' => 'db_error'], 500);
        }
    }

    public function refreshMV(): void {
        $service = new MaterializedRefreshService($this->db);
        $service->markDirty();
        $result = $service->refreshIfDirty(true);
        \Web\Lib\Http::json(['ok' => true, 'refresh' => $result], 200);
    }


    public function refreshVideoTimeSeries(): void {
        $pdo = $this->pdo();
        $repo = new VideosRepository($pdo);
        try {
            $repo->refreshVideoTimeSeries();
            Http::json(['ok' => true], 200);

        } catch (\Throwable $e) {
            error_log('[timeseries:refresh] '.SensitiveData::throwable($e));
            Http::json([
                'error' => 'refresh_failed',
            ], 500);
        }
    }






}
