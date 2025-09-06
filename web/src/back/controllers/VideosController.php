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
     *  - size (1..200, défaut 20)
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
        if ($size > 200) $size = 200;
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

        \Web\Lib\Http::json([
            'page'  => $page,
            'size'  => $size,
            'total' => $total,
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
}
