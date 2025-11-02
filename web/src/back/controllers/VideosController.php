<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Auth;
use Web\Db;
use Web\Lib\Http;

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
     *  - sort: views_desc (def), published_desc, published_asc, engagement_desc, watch_eq_desc, title_asc, title_desc
     */
    public function list(): void
    {
        $pdo = $this->pdo();

        $page = max(1, (int)($_GET['page'] ?? 1));
        $size = (int)($_GET['size'] ?? 20);
        if ($size < 1)   $size = 20;
        $offset = ($page - 1) * $size;

        $q        = isset($_GET['q']) ? trim((string)$_GET['q']) : null;
        $platform = isset($_GET['platform']) ? strtoupper(trim((string)$_GET['platform'])) : null;
        $format   = isset($_GET['format']) ? strtoupper(trim((string)$_GET['format'])) : null;

        $from = isset($_GET['from']) ? trim((string)$_GET['from']) : null;
        $to   = isset($_GET['to'])   ? trim((string)$_GET['to'])   : null;

        $sort = isset($_GET['sort']) ? trim((string)$_GET['sort']) : 'views_desc';

        // WHERE dynamiques
        $where = [];
        $args  = [];

        if ($q !== null && $q !== '') {
            $where[] = '(v.video_title LIKE ? OR v.slug LIKE ?)';
            $args[] = '%' . $q . '%';
            $args[] = '%' . $q . '%';
        }

        if ($from) { $where[] = 'v.video_published_at >= ?'; $args[] = $from; }
        if ($to)   { $where[] = 'v.video_published_at <  ?'; $args[] = $to;   }

        if ($platform) {
            switch ($platform) {
                case 'YOUTUBE':  $where[] = '(v.views_yt IS NOT NULL AND v.views_yt > 0)'; break;
                case 'FACEBOOK': $where[] = '(v.views_fb IS NOT NULL AND v.views_fb > 0)'; break;
                case 'INSTAGRAM':$where[] = '(v.views_ig IS NOT NULL AND v.views_ig > 0)'; break;
                case 'TIKTOK':   $where[] = '(v.views_tt IS NOT NULL AND v.views_tt > 0)'; break;
                default: /* ignore */;
            }
        }

        if ($format && in_array($format, ['VIDEO','SHORT','REEL'], true)) {
            $where[] = 'EXISTS (SELECT 1 FROM source_video sv WHERE sv.video_id = v.video_id AND sv.platform_format = ?)';
            $args[] = $format;
        }

        $whereSql = $where ? ('WHERE ' . implode(' AND ', $where)) : '';

        // Tri (NULLS LAST compatible MySQL/MariaDB)
        $orderBy = match ($sort) {
            'published_desc' => 'v.video_published_at DESC, v.video_id DESC',
            'published_asc'  => 'v.video_published_at ASC,  v.video_id ASC',
            'engagement_desc'=> '(v.engagement_rate_sum IS NULL) ASC, v.engagement_rate_sum DESC, v.views_native_sum DESC',
            'watch_eq_desc'  => '(v.watch_equivalent_sum IS NULL) ASC, v.watch_equivalent_sum DESC, v.views_native_sum DESC',
            'title_asc'      => 'v.video_title ASC,  v.video_id DESC',
            'title_desc'     => 'v.video_title DESC, v.video_id DESC',
            default          => 'v.views_native_sum DESC, v.video_published_at DESC'
        };

        // Count
        $sqlCount = "SELECT COUNT(*) FROM v_video_latest_rollup v $whereSql";
        $stCount = $pdo->prepare($sqlCount);
        $stCount->execute($args);
        $total = (int)$stCount->fetchColumn();

        // Page (tout en positionnel: LIMIT ? OFFSET ?)
        // Ajout MINIMAL: calcul de last_snapshot_at via sous-requête corrélée
        $sql = "
            SELECT
              v.video_id, v.slug, v.video_title, v.video_published_at, v.canonical_length_seconds,
              v.views_native_sum, v.likes_sum, v.comments_sum, v.shares_sum,
              v.total_watch_seconds_sum, v.avg_watch_ratio_est, v.watch_equivalent_sum, v.engagement_rate_sum,
              v.views_yt, v.views_fb, v.views_ig, v.views_tt,
              (
                SELECT MAX(ms.created_at)
                FROM source_video sv2
                JOIN metric_snapshot ms ON ms.source_video_id = sv2.id
                WHERE sv2.video_id = v.video_id
              ) AS last_snapshot_at
            FROM v_video_latest_rollup v
            $whereSql
            ORDER BY $orderBy
            LIMIT ? OFFSET ?
        ";
        $st = $pdo->prepare($sql);
        $execArgs = array_merge($args, [$size, $offset]);
        $st->execute($execArgs);

        $items = [];
        while ($row = $st->fetch(\PDO::FETCH_ASSOC)) {
            $items[] = [
                'id'         => (int)$row['video_id'],
                'slug'       => $row['slug'],
                'title'      => $row['video_title'],
                'published_at' => $row['video_published_at'],
                'length_seconds' => $row['canonical_length_seconds'] !== null ? (int)$row['canonical_length_seconds'] : null,

                'views_native_sum' => $row['views_native_sum'] !== null ? (int)$row['views_native_sum'] : null,
                'likes_sum'        => $row['likes_sum']        !== null ? (int)$row['likes_sum']        : null,
                'comments_sum'     => $row['comments_sum']     !== null ? (int)$row['comments_sum']     : null,
                'shares_sum'       => $row['shares_sum']       !== null ? (int)$row['shares_sum']       : null,

                'total_watch_seconds_sum' => $row['total_watch_seconds_sum'] !== null ? (int)$row['total_watch_seconds_sum'] : null,
                'avg_watch_ratio_est'     => $row['avg_watch_ratio_est'] !== null ? (float)$row['avg_watch_ratio_est'] : null,
                'watch_equivalent_sum'    => $row['watch_equivalent_sum'] !== null ? (float)$row['watch_equivalent_sum'] : null,
                'engagement_rate_sum'     => $row['engagement_rate_sum'] !== null ? (float)$row['engagement_rate_sum'] : null,

                'by_platform' => [
                    'YOUTUBE'  => $row['views_yt'] !== null ? (int)$row['views_yt'] : 0,
                    'FACEBOOK' => $row['views_fb'] !== null ? (int)$row['views_fb'] : 0,
                    'INSTAGRAM'=> $row['views_ig'] !== null ? (int)$row['views_ig'] : 0,
                    'TIKTOK'   => $row['views_tt'] !== null ? (int)$row['views_tt'] : 0,
                ],

                // Ajout minimal requis par l’IHM Étape 2
                'last_snapshot_at' => $row['last_snapshot_at'],
            ];
        }

        // SUM
        $sqlSum = "
          SELECT
            COALESCE(SUM(views_yt),0) AS sum_yt,
            COALESCE(SUM(views_fb),0) AS sum_fb,
            COALESCE(SUM(views_ig),0) AS sum_ig,
            COALESCE(SUM(views_tt),0) AS sum_tt
          FROM v_video_latest_rollup
        ";
        $stSum = $pdo->prepare($sqlSum);
        $stSum->execute();
        $row = $stSum->fetch(\PDO::FETCH_ASSOC) ?: ['sum_yt'=>0,'sum_fb'=>0,'sum_ig'=>0,'sum_tt'=>0];
        $sum = [
          'youtube'   => (int)$row['sum_yt'],
          'facebook'  => (int)$row['sum_fb'],
          'instagram' => (int)$row['sum_ig'],
          'tiktok'    => (int)$row['sum_tt'],
        ];

        \Web\Lib\Http::json([
            'page'  => $page,
            'size'  => $size,
            'total' => $total,
            'sum'   => $sum,
            'items' => $items
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
        $st = $pdo->prepare('SELECT * FROM v_video_latest_rollup WHERE video_id = ?');
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
            \Web\Lib\Http::json(['error' => 'db_error', 'message' => $e->getMessage()], 500);
        }
    }

}