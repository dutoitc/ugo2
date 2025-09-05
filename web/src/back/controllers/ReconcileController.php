<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Util;
use Web\Lib\Http;
use PDO;

final class ReconcileController
{
    public function __construct(private Db $db, private Auth $auth) {}

    /**
     * POST /api/v1/reconcile:run
     * Body JSON (optionnel): {"from":"YYYY-MM-DD HH:MM:SS","to":"YYYY-MM-DD HH:MM:SS","hoursWindow":48,"dryRun":true}
     */
    public function run(): void
    {
        $in = [];
        $raw = file_get_contents('php://input') ?: '';
        if ($raw !== '') { $j = json_decode($raw, true); if (is_array($j)) $in = $j; }

        $from = isset($in['from']) ? (string)$in['from'] : null;
        $to   = isset($in['to'])   ? (string)$in['to']   : null;
        $winH = isset($in['hoursWindow']) ? max(1, (int)$in['hoursWindow']) : 48;
        $dry  = (bool)($in['dryRun'] ?? false);

        $pdo = $this->db->pdo();

        // 1) Appliquer les overrides en attente
        $appliedOverrides = $this->applyOverrides($pdo);

        // 2) Charger les sources actives et non verrouillées (via video.is_locked)
        [$where, $params] = $this->buildWhere($from, $to);
        $st = $pdo->prepare("
          SELECT
            s.id,
            s.platform,
            s.platform_format,
            s.platform_video_id,
            s.video_id,
            s.title,
            s.description,
            s.url,
            s.duration_seconds,
            s.published_at,
            COALESCE(v.is_locked, 0) AS is_locked
          FROM source_video s
          LEFT JOIN video v ON v.id = s.video_id
          WHERE s.is_active = 1
            AND COALESCE(v.is_locked, 0) = 0
            $where
          ORDER BY s.published_at ASC, s.id ASC
        ");
        $st->execute($params);
        $rows = $st->fetchAll(PDO::FETCH_ASSOC);

        // 3) Clustering par titre normalisé + proximité temporelle (fenêtre winH heures)
        $clusters = $this->cluster($rows, $winH);

        if ($dry) {
            Http::json([
                'ok' => true,
                'dryRun' => true,
                'stats' => [
                    'clusters' => count($clusters),
                    'appliedOverrides' => $appliedOverrides,
                ]
            ]);
            return;
        }

        $createdVideos = 0;
        $linkedSources = 0;

        $this->db->tx(function(PDO $tx) use ($clusters, &$createdVideos, &$linkedSources) {
            $insVideo = $tx->prepare("
              INSERT INTO video (title, description, published_at, is_locked)
              VALUES (?, ?, ?, 0)
            ");
            $updSrc   = $tx->prepare("UPDATE source_video SET video_id = ? WHERE id = ?");

            foreach ($clusters as $cluster) {
                $vid = $this->pickExistingVideoId($cluster['items']);
                if ($vid === null) {
                    [$title, $desc, $officialAt] = $this->pickCanonical($cluster['items']);
                    $insVideo->execute([$title, $desc, $officialAt]);
                    $vid = (int)$tx->lastInsertId();
                    $createdVideos++;
                }
                foreach ($cluster['items'] as $it) {
                    if ((int)($it['video_id'] ?? 0) !== (int)$vid) {
                        $updSrc->execute([$vid, (int)$it['id']]);
                        $linkedSources++;
                    }
                }
            }
        });

        Http::json([
            'ok' => true,
            'stats' => [
                'clusters' => count($clusters),
                'createdVideos' => $createdVideos,
                'linkedSources' => $linkedSources,
                'appliedOverrides' => $appliedOverrides
            ]
        ]);
    }

    private function buildWhere(?string $from, ?string $to): array {
        $conds = []; $params = [];
        if ($from) { $conds[] = "s.published_at >= ?"; $params[] = $from; }
        if ($to)   { $conds[] = "s.published_at <= ?"; $params[] = $to; }
        $where = $conds ? (" AND ".implode(" AND ", $conds)) : "";
        return [$where, $params];
    }

    /**
     * Applique les overrides présents dans reconcile_override puis les supprime.
     * Actions supportées:
     *  - LINK   : cible un video_id (target_video_id non-null) -> set source_video.video_id = target
     *  - UNLINK : ignore target -> set source_video.video_id = NULL
     */
    private function applyOverrides(PDO $pdo): int {
        $rows = $pdo->query("
          SELECT id, source_video_id, action, target_video_id
          FROM reconcile_override
          ORDER BY id ASC
        ")->fetchAll(PDO::FETCH_ASSOC) ?: [];

        if (!$rows) return 0;

        $updLink   = $pdo->prepare("UPDATE source_video SET video_id = ? WHERE id = ?");
        $updUnlink = $pdo->prepare("UPDATE source_video SET video_id = NULL WHERE id = ?");
        $del       = $pdo->prepare("DELETE FROM reconcile_override WHERE id = ?");

        $applied = 0;
        $this->db->tx(function(PDO $tx) use ($rows, $updLink, $updUnlink, $del, &$applied) {
            foreach ($rows as $r) {
                $sid = (int)$r['source_video_id'];
                $act = strtoupper((string)$r['action']);
                $tgt = isset($r['target_video_id']) ? (int)$r['target_video_id'] : null;

                if ($act === 'LINK' && $tgt !== null) {
                    $updLink->execute([$tgt, $sid]);
                    $applied++;
                } elseif ($act === 'UNLINK') {
                    $updUnlink->execute([$sid]);
                    $applied++;
                }
                $del->execute([(int)$r['id']]);
            }
        });
        return $applied;
    }

    private function cluster(array $rows, int $hoursWindow): array {
        $groups = [];
        foreach ($rows as $r) {
            $norm = $this->normalizeTitle((string)($r['title'] ?? ''));
            $ts   = strtotime((string)($r['published_at'] ?? ''));
            if (!$ts) continue;

            if (!isset($groups[$norm])) $groups[$norm] = [];
            $placed = false;
            for ($i = 0; $i < count($groups[$norm]); $i++) {
                $b = $groups[$norm][$i];
                if (abs($ts - $b['refTs']) <= $hoursWindow * 3600) {
                    $groups[$norm][$i]['items'][] = $r;
                    $groups[$norm][$i]['refTs']   = (int)round(($b['refTs'] + $ts) / 2);
                    $placed = true;
                    break;
                }
            }
            if (!$placed) {
                $groups[$norm][] = ['refTs' => $ts, 'items' => [$r]];
            }
        }

        $clusters = [];
        foreach ($groups as $key => $buckets) {
            foreach ($buckets as $b) {
                $clusters[] = ['normTitle' => $key, 'refTs' => $b['refTs'], 'items' => $b['items']];
            }
        }
        return $clusters;
    }

    private function normalizeTitle(string $t): string {
        $t = mb_strtolower($t);
        $t = preg_replace('/\b(teaser|bande-annonce|preview|extrait)\b/u', '', $t) ?? $t;
        $t = preg_replace('/[\s\-_:;,.!?()\[\]{}]+/u', ' ', $t) ?? $t;
        return trim($t);
    }

    private function pickExistingVideoId(array $items): ?int {
        foreach ($items as $it) {
            $vid = $it['video_id'] ?? null;
            if ($vid !== null) return (int)$vid;
        }
        return null;
    }

    /**
     * Canonicalisation simple :
     *  - Titre : priorité YouTube non-vide, sinon le plus long non-vide, sinon "(sans titre)"
     *  - Desc. : même stratégie
     *  - Date officielle : la plus ancienne des items du cluster
     */
    private function pickCanonical(array $items): array {
        // Title
        $ytTitleItems = array_values(array_filter($items, function($it){
            $p = strtoupper((string)($it['platform'] ?? ''));
            $t = trim((string)($it['title'] ?? ''));
            return $p === 'YOUTUBE' && $t !== '';
        }));
        if ($ytTitleItems) {
            $title = (string)$ytTitleItems[0]['title'];
        } else {
            $candsTitle = array_values(array_filter($items, fn($it)=> trim((string)($it['title']??'')) !== ''));
            if (!$candsTitle) $candsTitle = $items;
            usort($candsTitle, fn($a,$b)=> mb_strlen((string)($b['title']??'')) <=> mb_strlen((string)($a['title']??'')));
            $title = (string)($candsTitle[0]['title'] ?? '(sans titre)');
        }

        // Description
        $ytDescItems = array_values(array_filter($items, function($it){
            $p = strtoupper((string)($it['platform'] ?? ''));
            $d = trim((string)($it['description'] ?? ''));
            return $p === 'YOUTUBE' && $d !== '';
        }));
        if ($ytDescItems) {
            $desc = (string)$ytDescItems[0]['description'];
        } else {
            $candsDesc = array_values(array_filter($items, fn($it)=> trim((string)($it['description']??'')) !== ''));
            if (!$candsDesc) $candsDesc = $items;
            usort($candsDesc, fn($a,$b)=> mb_strlen((string)($b['description']??'')) <=> mb_strlen((string)($a['description']??'')));
            $desc = (string)($candsDesc[0]['description'] ?? '');
        }

        // Official published_at = plus ancienne date du cluster
        $dates = array_map(fn($it)=> (string)($it['published_at'] ?? ''), $items);
        $dates = array_values(array_filter($dates, fn($d)=> $d !== '' ));
        sort($dates, SORT_STRING);
        $officialAt = $dates[0] ?? date('Y-m-d H:i:s');

        return [$title, $desc, $officialAt];
    }
}
