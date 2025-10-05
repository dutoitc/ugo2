<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Lib\Http;
use PDO;
use DateTimeImmutable;
use DateTimeZone;

final class VideoTimeseriesController
{
    public function __construct(private Db $db, private Auth $auth) {}

    /**
     * GET /api/v1/video/{id}/timeseries
     * Params (query, optionnels):
     * - metric: views_native | likes | comments | shares | total_watch_seconds (def=views_native)
     * - interval: hour | day (def=hour)
     * - range: "24h" | "7d" | "30d" ... (def=7d)
     * - platforms: CSV ex "FACEBOOK,YOUTUBE" (def=toutes)
     * - agg: sum | cumsum (def=sum)   // cumsum = cumul sur les buckets
     * - limit: entier (>0) pour downsampling (def: 0 = désactivé)
     *
     * NB: Pour des compteurs cumulés on agrège intra-bucket par MAX (par plateforme),
     * puis on somme entre plateformes pour la série globale.
     */
    public function timeseries(): void
    {
        // $this->auth->assertApiKeyRead(); // activer si nécessaire

        $videoId = (int)($_GET['id'] ?? 0);
        if ($videoId <= 0) {
            $path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?? '/';
            if (preg_match('#/api/v1/video/(\d+)/timeseries$#', $path, $m)) {
                $videoId = (int)$m[1];
            }
        }
        if ($videoId <= 0) {
            Http::json(['error' => 'bad_request', 'message' => 'missing video id'], 400);
            return;
        }

        $metric     = $this->pickMetric((string)($_GET['metric'] ?? 'views_native'));
        $interval   = $this->pickInterval((string)($_GET['interval'] ?? 'hour'));
        $agg        = $this->pickAgg((string)($_GET['agg'] ?? 'sum'));
        $rangeStr   = (string)($_GET['range'] ?? '7d');
        $platforms  = isset($_GET['platforms']) && trim((string)$_GET['platforms']) !== ''
                        ? $this->sanitizePlatformsCsv((string)$_GET['platforms'])
                        : null;
        $limit      = isset($_GET['limit']) ? max(0, (int)$_GET['limit']) : 0;

        [$fromUtc, $fromStr] = $this->computeFromUtc($rangeStr);
        $toStr = $this->nowUtcStr();

        // Bucket de temps (UTC)
        $tsExpr = $interval === 'day'
            ? "DATE_FORMAT(ms.snapshot_at, '%Y-%m-%d 00:00:00.000')"
            : "DATE_FORMAT(ms.snapshot_at, '%Y-%m-%d %H:00:00.000')";

        // Métrique (whitelist)
        $metricExpr = match ($metric) {
            'views_native'        => 'COALESCE(ms.views_native,0)',
            'likes'               => 'COALESCE(ms.likes,0)',
            'comments'            => 'COALESCE(ms.comments,0)',
            'shares'              => 'COALESCE(ms.shares,0)',
            'total_watch_seconds' => 'COALESCE(ms.total_watch_seconds,0)',
            default               => 'COALESCE(ms.views_native,0)',
        };

        // 1) Série par plateforme = MAX intra-bucket
        $byPlatform = $this->fetchByPlatformMaxPerBucket($videoId, $fromStr, $tsExpr, $metricExpr, $platforms);

        // 2) Série globale = somme des MAX par ts
        $global = $this->sumPlatformsPerBucket($byPlatform);

        // Logs pour enquête collecte (non-monotonicité par plateforme)
        $this->warnIfNonMonotonic($byPlatform);

        // 3) Agg cumsum (optionnel)
        if ($agg === 'cumsum') {
            $global = $this->cumsum($global);
            foreach ($byPlatform as $p => $rows) {
                $byPlatform[$p] = $this->cumsum($rows);
            }
        }

        // 4) Downsampling (optionnel)
        if ($limit > 0) {
            $global = $this->downsample($global, $limit);
            foreach ($byPlatform as $p => $rows) {
                $byPlatform[$p] = $this->downsample($rows, $limit);
            }
        }

        // 5) Sortie au format existant
        $out = [
            'timeseries'  => [
                'views' => array_map(static fn($r) => ['ts' => (string)$r['ts'], 'value' => (int)$r['value']], $global),
            ],
            'granularity' => $interval,
            'metric'      => $metric,
            'from'        => $fromStr,
            'to'          => $toStr,
        ];
        foreach ($byPlatform as $platform => $rows) {
            $out['timeseries'][$platform] = array_map(
                static fn($r) => ['ts' => (string)$r['ts'], 'value' => (int)$r['value']],
                $rows
            );
        }

        Http::json($out, 200);
    }

    // ------------------- internals -------------------

    private function pickMetric(string $m): string {
        $allowed = ['views_native','likes','comments','shares','total_watch_seconds'];
        $m = strtolower($m);
        return in_array($m, $allowed, true) ? $m : 'views_native';
    }

    private function pickInterval(string $i): string {
        $i = strtolower($i);
        return $i === 'day' ? 'day' : 'hour';
    }

    private function pickAgg(string $a): string {
        $a = strtolower($a);
        return $a === 'cumsum' ? 'cumsum' : 'sum';
    }

    private function sanitizePlatformsCsv(string $csv): string {
        $parts = array_filter(array_map(
            static fn($x) => strtoupper(trim(preg_replace('/[^A-Z0-9_,]/i', '', $x))),
            explode(',', $csv)
        ));
        $parts = array_values(array_unique($parts));
        return implode(',', $parts);
    }

    /** @return array{0:DateTimeImmutable,1:string} 'Y-m-d H:i:s.000' UTC */
    private function computeFromUtc(string $range): array {
        $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));
        if (preg_match('/^(\d+)([hd])$/i', $range, $m)) {
            $n = (int)$m[1];
            $u = strtolower($m[2]);
            $from = $u === 'h' ? $now->modify("-{$n} hours") : $now->modify("-{$n} days");
        } else {
            $from = $now->modify('-7 days');
        }
        return [$from, $from->format('Y-m-d H:i:s') . '.000'];
    }

    private function nowUtcStr(): string {
        $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));
        return $now->format('Y-m-d H:i:s') . '.000';
    }

    /**
     * Par plateforme: MAX intra-bucket (ts), trié ASC.
     * @return array<string, array<int,array{ts:string,value:int}>>
     */
    private function fetchByPlatformMaxPerBucket(
        int $videoId,
        string $fromStr,
        string $tsExpr,
        string $metricExpr,
        ?string $platformsCsv
    ): array {
        $pdo = $this->db->pdo();
        $wherePlatforms = $platformsCsv ? " AND FIND_IN_SET(sv.platform, :platforms) > 0" : "";
        $sql = "
            SELECT t.platform, t.ts, t.value
            FROM (
                SELECT sv.platform AS platform,
                       {$tsExpr} AS ts,
                       MAX({$metricExpr}) AS value
                FROM metric_snapshot ms
                JOIN source_video sv ON sv.id = ms.source_video_id
                WHERE sv.video_id = :vid
                  AND ms.snapshot_at >= :fromTs
                  {$wherePlatforms}
                GROUP BY sv.platform, ts
            ) t
            ORDER BY t.platform, t.ts
        ";
        $st = $pdo->prepare($sql);
        $st->bindValue(':vid', $videoId, PDO::PARAM_INT);
        $st->bindValue(':fromTs', $fromStr, PDO::PARAM_STR);
        if ($platformsCsv) $st->bindValue(':platforms', $platformsCsv, PDO::PARAM_STR);
        $st->execute();

        $out = [];
        while ($row = $st->fetch(PDO::FETCH_ASSOC)) {
            $p = (string)($row['platform'] ?? 'UNKNOWN');
            $out[$p] ??= [];
            $out[$p][] = [
                'ts'    => (string)$row['ts'],
                'value' => (int)($row['value'] ?? 0),
            ];
        }
        return $out;
    }

    /**
     * Global = somme des MAX par plateforme pour chaque ts, trié ASC.
     * @param array<string, array<int,array{ts:string,value:int}>> $byPlatform
     * @return array<int,array{ts:string,value:int}>
     */
    private function sumPlatformsPerBucket(array $byPlatform): array
    {
        // Collecte et union des timestamps
        $bucketSet = [];
        foreach ($byPlatform as $rows) {
            foreach ($rows as $r) $bucketSet[$r['ts']] = true;
        }
        $buckets = array_keys($bucketSet);
        sort($buckets);

        $out = [];
        foreach ($buckets as $ts) {
            $sum = 0;
            foreach ($byPlatform as $rows) {
                // recherche rapide: dernier point de ce ts (les tableaux sont petits → boucle)
                foreach ($rows as $r) {
                    if ($r['ts'] === $ts) { $sum += (int)$r['value']; break; }
                }
            }
            $out[] = ['ts' => $ts, 'value' => $sum];
        }
        return $out;
    }

    /** Cumul croissant (points triés ASC) */
    private function cumsum(array $points): array {
        $sum = 0;
        foreach ($points as &$p) {
            $sum += (int)$p['value'];
            $p['value'] = $sum;
        }
        return $points;
    }

    /**
     * Downsample simple: conserve ~limit points espacés uniformément.
     * @param array<int,array{ts:string,value:int}> $points
     */
    private function downsample(array $points, int $limit): array {
        $n = count($points);
        if ($limit <= 0 || $n <= $limit) return $points;
        $step = (int)ceil($n / $limit);
        $out = [];
        for ($i = 0; $i < $n; $i += $step) {
            $out[] = $points[$i];
        }
        if ($out && $out[count($out)-1]['ts'] !== $points[$n-1]['ts']) {
            $out[] = $points[$n-1];
        }
        return $out;
    }

    /**
     * Log d'aide au debug collecte : si une plateforme décroit dans le temps,
     * on l'indique dans les logs pour investigation côté batch.
     */
    private function warnIfNonMonotonic(array $byPlatform): void
    {
        foreach ($byPlatform as $platform => $rows) {
            $prev = null;
            foreach ($rows as $r) {
                $v = (int)$r['value'];
                if ($prev !== null && $v < $prev) {
                    error_log(sprintf(
                        '[API] WARN non_monotonic platform=%s ts=%s prev=%d cur=%d',
                        $platform, $r['ts'], $prev, $v
                    ));
                    break; // un log par plateforme suffit
                }
                $prev = $v;
            }
        }
    }
}
