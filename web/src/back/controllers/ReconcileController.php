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
     * Body JSON (optionnel): {"from":"...Z","to":"...Z","hoursWindow":48,"dryRun":true}
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

        // 1) Applique les overrides et purge la table d’override (l’audit reste)
        $appliedOverrides = $this->applyOverrides($pdo);

        // 2) Charge les sources non verrouillées dans la fenêtre temporelle
        [$where, $params] = $this->buildWhere($from, $to);
        $st = $pdo->prepare("
          SELECT id, platform, platform_source_id, video_id, is_teaser, locked,
                 title, description, permalink_url, media_type, duration_seconds, published_at
          FROM source_video
          WHERE locked = 0
          $where
          ORDER BY published_at ASC, id ASC
        ");
        $st->execute($params);
        $rows = $st->fetchAll();

        // 3) Cluster par titre normalisé + proximité temporelle
        $clusters = $this->cluster($rows, $winH);

        if ($dry) {
            Http::json(['ok'=>true, 'dryRun'=>true, 'stats'=>[
                'clusters'=>count($clusters),
                'appliedOverrides'=>$appliedOverrides,
            ]]);
            return;
        }

        $createdVideos = 0; $linkedSources = 0;

        $this->db->tx(function(PDO $tx) use ($clusters, &$createdVideos, &$linkedSources) {
            $insVideo = $tx->prepare("
              INSERT INTO video (ext_uid, canonical_title, canonical_description, official_published_at, location_id)
              VALUES (NULL, ?, ?, ?, NULL)
            ");
            $updSrc   = $tx->prepare("UPDATE source_video SET video_id=? WHERE id=?");

            foreach ($clusters as $cluster) {
                $vid = $this->pickExistingVideoId($cluster['items']);
                if ($vid === null) {
                    [$title,$desc,$officialAt] = $this->pickCanonical($cluster['items']);
                    $insVideo->execute([$title, $desc, $officialAt]);
                    $vid = (int)$tx->lastInsertId();
                    $createdVideos++;
                }
                foreach ($cluster['items'] as $it) {
                    if ((int)$it['video_id'] !== (int)$vid) {
                        $updSrc->execute([$vid, $it['id']]);
                        $linkedSources++;
                    }
                }
            }
        });

        Http::json(['ok'=>true, 'stats'=>[
            'clusters'=>count($clusters),
            'createdVideos'=>$createdVideos,
            'linkedSources'=>$linkedSources,
            'appliedOverrides'=>$appliedOverrides
        ]]);
    }

    private function buildWhere(?string $from, ?string $to): array {
        $conds=[]; $params=[];
        if ($from) { $conds[]="published_at >= ?"; $params[]=$from; }
        if ($to)   { $conds[]="published_at <= ?"; $params[]=$to; }
        $where = $conds ? ("AND ".implode(" AND ", $conds)) : "";
        return [$where,$params];
    }

    private function applyOverrides(PDO $pdo): int {
        $rows = $pdo->query("SELECT id, source_video_id, action, target_video_id FROM reconcile_override ORDER BY id ASC")->fetchAll();
        if (!$rows) return 0;

        $upd = $pdo->prepare("
          UPDATE source_video
          SET video_id = CASE WHEN ? IS NULL THEN NULL ELSE ? END,
              is_teaser = CASE WHEN ?='TEASER' THEN 1 WHEN ?='MAIN' THEN 0 ELSE is_teaser END,
              locked    = CASE WHEN ?='LOCK' THEN 1 WHEN ?='UNLOCK' THEN 0 ELSE locked END
          WHERE id = ?
        ");
        $del = $pdo->prepare("DELETE FROM reconcile_override WHERE id=?");

        $applied = 0;
        $this->db->tx(function(PDO $tx) use ($rows,$upd,$del,&$applied) {
            foreach ($rows as $r) {
                $sid = (int)$r['source_video_id'];
                $act = (string)$r['action'];
                $tgt = $r['target_video_id'] !== null ? (int)$r['target_video_id'] : null;
                $upd->execute([$tgt,$tgt,$act,$act,$act,$act,$sid]);
                $del->execute([(int)$r['id']]);
                $applied++;
            }
        });
        return $applied;
    }

    private function cluster(array $rows, int $hoursWindow): array {
        $groups=[];
        foreach ($rows as $r) {
            $norm = $this->normalizeTitle((string)($r['title'] ?? ''));
            $ts   = strtotime((string)$r['published_at']); if (!$ts) continue;
            if (!isset($groups[$norm])) $groups[$norm]=[];
            $placed=false;
            for ($i=0; $i<count($groups[$norm]); $i++) {
                $b=$groups[$norm][$i];
                if (abs($ts-$b['refTs']) <= $hoursWindow*3600) {
                    $groups[$norm][$i]['items'][]=$r;
                    $groups[$norm][$i]['refTs']=(int)round(($b['refTs']+$ts)/2);
                    $placed=true; break;
                }
            }
            if (!$placed) $groups[$norm][]= ['refTs'=>$ts,'items'=>[$r]];
        }
        $clusters=[];
        foreach ($groups as $key=>$buckets) foreach ($buckets as $b) {
            $clusters[]=['normTitle'=>$key,'refTs'=>$b['refTs'],'items'=>$b['items']];
        }
        return $clusters;
    }

    private function normalizeTitle(string $t): string {
        $t = mb_strtolower($t);
        $t = preg_replace('/\\b(teaser|bande-annonce|preview|extrait)\\b/u','',$t) ?? $t;
        $t = preg_replace('/[\\s\\-_:;,.!?()\\[\\]{}]+/u',' ',$t) ?? $t;
        return trim($t);
    }

    private function pickExistingVideoId(array $items): ?int {
        foreach ($items as $it) if (!empty($it['video_id'])) return (int)$it['video_id'];
        return null;
    }

       private function pickCanonical(array $items): array {
           // 1) Titre: priorité YouTube si dispo et non-vide, sinon fallback "longest non-empty"
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

           // 2) Description: priorité YouTube si dispo et non-vide, sinon fallback "longest non-empty"
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
               $desc  = (string)($candsDesc[0]['description'] ?? '');
           }

           // 3) Date "officielle" = plus ancienne parmi les non-teasers (sinon plus ancienne tout court)
           $cands = array_values(array_filter($items, fn($it)=> (int)($it['is_teaser']??0)===0));
           if (!$cands) $cands = $items;
           usort($cands, fn($a,$b)=> strcmp((string)$a['published_at'], (string)$b['published_at']));
           $officialAt = (string)($cands[0]['published_at'] ?? date('Y-m-d H:i:s'));

           return [$title,$desc,$officialAt];
       }

}
