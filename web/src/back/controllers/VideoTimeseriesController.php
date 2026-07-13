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

    public function timeseries(): void
    {
        // $this->auth->assertApiKeyRead();

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
        $rangeStr   = (string)($_GET['range'] ?? 'all');
        $platforms  = isset($_GET['platforms']) && trim((string)$_GET['platforms']) !== ''
            ? $this->sanitizePlatformsCsv((string)$_GET['platforms'])
            : null;
        $limit      = isset($_GET['limit']) ? max(0, (int)$_GET['limit']) : 0;

        $fromStr = $this->computeFromStr($videoId, $rangeStr);
        $toStr   = $this->nowUtcStr();

        $tsExpr = $interval === 'day'
            ? "DATE_FORMAT(ms.snapshot_at, '%Y-%m-%d 00:00:00.000')"
            : "DATE_FORMAT(ms.snapshot_at, '%Y-%m-%d %H:00:00.000')";

        $metricExpr = match ($metric) {
            'views_native'        => 'COALESCE(ms.views_native,0)',
            'likes'               => 'COALESCE(ms.likes,0)',
            'comments'            => 'COALESCE(ms.comments,0)',
            'shares'              => 'COALESCE(ms.shares,0)',
            'total_watch_seconds' => 'COALESCE(ms.total_watch_seconds,0)',
            default               => 'COALESCE(ms.views_native,0)',
        };

        // ✅ IMPORTANT: on borne aussi par "to"
        $byPlatform = $this->fetchByPlatformMaxPerBucket(
            $videoId, $fromStr, $toStr, $tsExpr, $metricExpr, $platforms
        );

        $global = $this->sumPlatformsPerBucket($byPlatform);
        $this->warnIfNonMonotonic($byPlatform);

        if ($agg === 'cumsum') {
            $global = $this->cumsum($global);
            foreach ($byPlatform as $p => $rows) {
                $byPlatform[$p] = $this->cumsum($rows);
            }
        }

        if ($limit > 0) {
            $global = $this->downsample($global, $limit);
            foreach ($byPlatform as $p => $rows) {
                $byPlatform[$p] = $this->downsample($rows, $limit);
            }
        }

        $messages = [];
        $messages[] = "range=$rangeStr";
        $messages[] = "interval=$interval";
        $messages[] = "metric=$metric";
        $messages[] = "from=$fromStr";
        $messages[] = "to=$toStr";
        $messages[] = "platforms=" . ($platforms ?? 'ALL');

        $out = [
            'timeseries' => [
                'views' => array_map(
                    static fn($r) => ['ts' => (string)$r['ts'], 'value' => (int)$r['value']],
                    $global
                ),
            ],
            'granularity' => $interval,
            'metric'      => $metric,
            'from'        => $fromStr,
            'to'          => $toStr,
            'message'     => implode(' | ', $messages),
        ];

        foreach ($byPlatform as $platform => $rows) {
            $out['timeseries'][$platform] = array_map(
                static fn($r) => ['ts' => (string)$r['ts'], 'value' => (int)$r['value']],
                $rows
            );
        }

        $include = (string)($_GET['include'] ?? '');

        if ($metric === 'views_native' && str_contains($include, 'percentiles')) {
            // ✅ cap percentiles à la durée réellement affichée (par plateforme)
            $publishedAt = $this->fetchVideoPublishedAt($videoId);
            $t0Aligned = $publishedAt ? $this->alignT0($publishedAt, $interval) : null;

            $maxBuckets = $t0Aligned
                ? $this->computeMaxBucketsFromTimeseries($t0Aligned, $interval, $byPlatform)
                : [];

            $out['percentiles'] = $this->fetchPercentileBands(
                $videoId,
                $interval,
                $platforms,
                $t0Aligned,
                $maxBuckets
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
        return strtolower($i) === 'day' ? 'day' : 'hour';
    }

    private function pickAgg(string $a): string {
        return strtolower($a) === 'cumsum' ? 'cumsum' : 'sum';
    }

    private function sanitizePlatformsCsv(string $csv): string {
        $parts = array_filter(array_map(
            static fn($x) => strtoupper(trim(preg_replace('/[^A-Z0-9_,]/i', '', $x))),
            explode(',', $csv)
        ));
        return implode(',', array_values(array_unique($parts)));
    }

    private function computeFromStr(int $videoId, string $range): string
    {
        $range = strtolower(trim($range));

        if ($range === '' || $range === 'all' || $range === 'full') {
            $publishedAt = $this->fetchVideoPublishedAt($videoId);
            if ($publishedAt !== null) {
                return $publishedAt;
            }
        }

        [, $fromStr] = $this->computeFromUtc($range);
        return $fromStr;
    }

    /** @return array{0:DateTimeImmutable,1:string} */
    private function computeFromUtc(string $range): array {
        $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));
        if (preg_match('/^(\d+)([hd])$/i', $range, $m)) {
            $n = (int)$m[1];
            $from = strtolower($m[2]) === 'h'
                ? $now->modify("-{$n} hours")
                : $now->modify("-{$n} days");
        } else {
            $from = $now->modify('-7 days');
        }
        return [$from, $from->format('Y-m-d H:i:s') . '.000'];
    }

    private function nowUtcStr(): string {
        return (new DateTimeImmutable('now', new DateTimeZone('UTC')))
            ->format('Y-m-d H:i:s') . '.000';
    }

    private function fetchVideoPublishedAt(int $videoId): ?string
    {
        $pdo = $this->db->pdo();
        $st = $pdo->prepare("
            SELECT published_at
            FROM video
            WHERE id = :vid
            LIMIT 1
        ");
        $st->bindValue(':vid', $videoId, PDO::PARAM_INT);
        $st->execute();

        $v = $st->fetchColumn();
        if (!$v) return null;

        return str_contains((string)$v, '.') ? (string)$v : ((string)$v . '.000');
    }

    private function fetchByPlatformMaxPerBucket(
        int $videoId,
        string $fromStr,
        string $toStr,
        string $tsExpr,
        string $metricExpr,
        ?string $platformsCsv
    ): array {
        $pdo = $this->db->pdo();
        $wherePlatforms = $platformsCsv ? " AND FIND_IN_SET(sv.platform, :platforms) > 0" : "";

        // ✅ borne haute ajoutée: ms.snapshot_at <= :toTs
        $sql = "
            SELECT t.platform, t.ts, t.value
            FROM (
                SELECT sv.platform,
                       {$tsExpr} AS ts,
                       MAX({$metricExpr}) AS value
                FROM metric_snapshot ms
                JOIN source_video sv ON sv.id = ms.source_video_id
                WHERE sv.video_id = :vid
                  AND ms.snapshot_at >= :fromTs
                  AND ms.snapshot_at <= :toTs
                  {$wherePlatforms}
                GROUP BY sv.platform, ts
            ) t
            ORDER BY t.platform, t.ts
        ";

        $st = $pdo->prepare($sql);
        $st->bindValue(':vid', $videoId, PDO::PARAM_INT);
        $st->bindValue(':fromTs', $fromStr, PDO::PARAM_STR);
        $st->bindValue(':toTs', $toStr, PDO::PARAM_STR);
        if ($platformsCsv) $st->bindValue(':platforms', $platformsCsv, PDO::PARAM_STR);
        $st->execute();

        $out = [];
        while ($row = $st->fetch(PDO::FETCH_ASSOC)) {
            $p = (string)$row['platform'];
            $out[$p][] = [
                'ts'    => (string)$row['ts'],
                'value' => (int)$row['value'],
            ];
        }
        return $out;
    }

    private function sumPlatformsPerBucket(array $byPlatform): array
    {
        $buckets = [];
        $index   = [];

        foreach ($byPlatform as $p => $rows) {
            foreach ($rows as $r) {
                $ts = $r['ts'];
                $buckets[$ts] = true;
                $index[$p][$ts] = (int)$r['value'];
            }
        }

        $tsList = array_keys($buckets);
        sort($tsList);

        $out = [];
        foreach ($tsList as $ts) {
            $sum = 0;
            foreach ($index as $map) {
                $sum += $map[$ts] ?? 0;
            }
            $out[] = ['ts' => $ts, 'value' => $sum];
        }
        return $out;
    }

    private function cumsum(array $points): array {
        $sum = 0;
        foreach ($points as &$p) {
            $sum += (int)$p['value'];
            $p['value'] = $sum;
        }
        return $points;
    }

    private function downsample(array $points, int $limit): array {
        $n = count($points);
        if ($limit <= 0 || $n <= $limit) return $points;
        $step = (int)ceil($n / $limit);
        $out = [];
        for ($i = 0; $i < $n; $i += $step) $out[] = $points[$i];
        if ($out && $out[count($out)-1]['ts'] !== $points[$n-1]['ts']) {
            $out[] = $points[$n-1];
        }
        return $out;
    }

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
                    break;
                }
                $prev = $v;
            }
        }
    }

    private function alignT0(string $publishedAt, string $granularity): DateTimeImmutable
    {
        $t0 = new DateTimeImmutable($publishedAt, new DateTimeZone('UTC'));
        if ($granularity === 'day') {
            return $t0->setTime(0, 0, 0);
        }
        // hour
        return $t0->setTime((int)$t0->format('H'), 0, 0);
    }

    /** @return array<string,int> platform => max age_bucket */
    private function computeMaxBucketsFromTimeseries(
        DateTimeImmutable $t0Aligned,
        string $granularity,
        array $byPlatform
    ): array {
        $out = [];
        foreach ($byPlatform as $platform => $rows) {
            if (empty($rows)) continue;
            $lastTsStr = (string)end($rows)['ts'];
            $tLast = new DateTimeImmutable($lastTsStr, new DateTimeZone('UTC'));

            if ($granularity === 'day') {
                $days = (int)$t0Aligned->diff($tLast)->days;
                $out[$platform] = max(0, $days);
            } else {
                $hours = (int)floor(($tLast->getTimestamp() - $t0Aligned->getTimestamp()) / 3600);
                $out[$platform] = max(0, $hours);
            }
        }
        return $out;
    }

    private function fetchPercentileBands(
        int $videoId,
        string $granularity,
        ?string $platformsCsv,
        ?DateTimeImmutable $t0Aligned,
        array $maxBuckets
    ): array {
        if ($t0Aligned === null) {
            return [];
        }

        $pdo = $this->db->pdo();

        $wherePlatforms = $platformsCsv
            ? "AND FIND_IN_SET(p.source, :platforms) > 0"
            : "";

        $sql = "
            SELECT
                p.source,
                p.age_bucket,
                p.p10_views,
                p.p25_views,
                p.p50_views,
                p.p75_views,
                p.p90_views,
                p.count_videos
            FROM mv_video_views_percentiles p
            WHERE p.granularity = :granularity
            {$wherePlatforms}
            ORDER BY p.source, p.age_bucket
        ";

        $st = $pdo->prepare($sql);
        $st->bindValue(':granularity', $granularity);
        if ($platformsCsv) {
            $st->bindValue(':platforms', $platformsCsv);
        }
        $st->execute();

        $out = [];

        while ($row = $st->fetch(PDO::FETCH_ASSOC)) {
            $source = (string)$row['source'];
            $bucket = (int)$row['age_bucket'];

            // ✅ stoppe exactement au dernier bucket réellement mesuré
            if (!isset($maxBuckets[$source]) || $bucket > $maxBuckets[$source]) {
                continue;
            }

            $ts = $granularity === 'day'
                ? $t0Aligned->modify("+{$bucket} days")
                : $t0Aligned->modify("+{$bucket} hours");

            $tsStr = $ts->format('Y-m-d H:i:s') . '.000';

            $out[$source]['count_videos'] = (int)$row['count_videos'];

            foreach (['p10','p25','p50','p75','p90'] as $p) {
                $col = $p . '_views';
                if ($row[$col] !== null) {
                    $out[$source][$p][] = [
                        'ts'    => $tsStr,
                        'value' => (int)$row[$col]
                    ];
                }
            }
        }

        return $out;
    }
}
