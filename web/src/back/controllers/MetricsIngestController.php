<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Auth;
use Web\Db;
use Web\Lib\Http;
use Web\Services\MetricsIngestService;

final class MetricsIngestController
{
    public function __construct(
        private Db $db,
        private Auth $auth
    ) {}

    /**
     * POST /api/v1/metrics:batchUpsert
     * Body: {"snapshots":[ {...}, {...} ]}
     */
    public function batchUpsert(): void
    {
        // Auth (garde comme chez toi)
        if (method_exists($this->auth, 'requireIngestAuth')) {
            $this->auth->requireIngestAuth();
        } elseif (method_exists($this->auth, 'requireValidRequest')) {
            $this->auth->requireValidRequest();
        }

        $raw = file_get_contents('php://input') ?: '';
        $json = json_decode($raw, true, 512, JSON_BIGINT_AS_STRING);

        if (!is_array($json) || !isset($json['snapshots']) || !is_array($json['snapshots'])) {
            Http::json(['error'=>'bad_request', 'message'=>'Body must be {"snapshots":[...]}'], 400);
            return;
        }

        $in = $json['snapshots'];

        // --- Normalisation tolérante ---------------------------------------------
        $norm = [];
        $skipped = 0;

        foreach ($in as $i => $s) {
            if (!is_array($s)) { $skipped++; continue; }

            // Plateforme
            $platform = strtoupper((string)($s['platform'] ?? 'YOUTUBE'));

            // ID vidéo
            $pvid = $s['platform_video_id']?? null;

            if ($pvid === null || $pvid === '') { $skipped++; continue; }
            $pvid = (string)$pvid;

            $norm[] = [
                'platform'          => $platform,
                'platform_video_id' => $pvid,
                'snapshot_at'       => $s['snapshot_at']  ?? null,
                'views_native'      => self::toNullableInt($s['views_native'] ?? null),
                'likes'             => self::toNullableInt($s['likes'] ?? null),
                'comments'          => self::toNullableInt($s['comments'] ?? null),

                'avg_watch_seconds'     => self::toNullableFloat($s['avg_watch_seconds'] ?? null),
                'total_watch_seconds'   => self::toNullableFloat($s['total_watch_seconds'] ?? null),
                'video_length_seconds'  => self::toNullableInt($s['video_length_seconds'] ?? null),

                'reach'                 => self::toNullableInt($s['reach'] ?? null),
                'unique_viewers'        => self::toNullableInt($s['unique_viewers'] ?? null),
                'shares'                => self::toNullableInt($s['shares'] ?? null),

                'reactions_total'       => self::toNullableInt($s['reactions_total'] ?? null),
                'reactions_like'        => self::toNullableInt($s['reactions_like'] ?? null),
                'reactions_love'        => self::toNullableInt($s['reactions_love'] ?? null),
                'reactions_wow'         => self::toNullableInt($s['reactions_wow'] ?? null),
                'reactions_haha'        => self::toNullableInt($s['reactions_haha'] ?? null),
                'reactions_sad'         => self::toNullableInt($s['reactions_sad'] ?? null),
                'reactions_angry'       => self::toNullableInt($s['reactions_angry'] ?? null),

                'legacy_views_3s'       => self::toNullableInt($s['legacy_views_3s'] ?? $s['views_3s'] ?? null),
            ];
        }

        // Petit log utile (1ère fois uniquement)
        error_log(sprintf(
            '[metrics:batchUpsert] recv=%d norm=%d skipped=%d sample=%s',
            count($in), count($norm), $skipped,
            json_encode($norm[0] ?? null, JSON_UNESCAPED_SLASHES|JSON_UNESCAPED_UNICODE)
        ));

        // --- Appel service --------------------------------------------------------
        $service = new MetricsIngestService($this->db);
        try {
            $result = $service->ingestBatch($norm);
            Http::json([
                'status' => 'ok',
                'ok'     => $result['ok'],
                'ko'     => $result['ko'],
                'items'  => $result['items'],
                'skipped_pre' => $skipped,
            ], 200);
        } catch (\Throwable $e) {
            error_log('[metrics:batchUpsert] ERROR: '.$e->getMessage());
            Http::json(['error'=>'internal_error', 'message'=>$e->getMessage()], 500);
        }
    }

    // --- Helpers --------------------------------------------------------------

    /** Accepte ISO string, epoch secondes, epoch millisecondes; renvoie ISO UTC */
    // Dans MetricsIngestController

    private static function toIsoUtc(mixed $v): ?string
    {
        if ($v === null || $v === '') return null;

        // Cas numériques (int/float/chaîne de chiffres) -> epoch s/ms
        if (is_int($v) || is_float($v) || (is_string($v) && ctype_digit($v))) {
            $num = (float)$v;
            // 13 chiffres => ms ; 10 => s
            if ($num > 1e12) $num /= 1000.0; // ms -> s
            $ts = (int)round($num);
            return gmdate('c', $ts); // ex: 2025-09-05T07:35:52+00:00
        }

        // Chaînes ISO variées, ou "Y-m-d H:i:s"
        $s = trim((string)$v);
        try {
            // Autorise "YYYY-mm-dd HH:ii:ss" en le traitant comme UTC
            if (preg_match('/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/', $s)) {
                $dt = new \DateTimeImmutable($s.' UTC');
            } else {
                $dt = new \DateTimeImmutable($s);
            }
            return $dt->setTimezone(new \DateTimeZone('UTC'))->format('c');
        } catch (\Throwable $e) {
            return null;
        }
    }

    private static function toNullableInt($v) {
        if ($v === null || $v === '') {
            return null;
        }

        // Accepte string numérique, int ou float → cast
        if (is_numeric($v)) {
            return (int)$v;
        }

        // Tente de parser en dernier recours
        $v2 = filter_var($v, FILTER_SANITIZE_NUMBER_INT);
        return $v2 === '' ? null : (int)$v2;
    }

    private static function toNullableFloat(mixed $v): ?float
    {
        if ($v === null || $v === '') {
            return null;
        }

        // True pour:  "23", "23.4", 23, 23.4, "0.9871", "1e3"
        if (is_numeric($v)) {
            return (float)$v;
        }

        // Dernier recours: extraction "1.234", "123"
        $v2 = filter_var($v, FILTER_SANITIZE_NUMBER_FLOAT, FILTER_FLAG_ALLOW_FRACTION | FILTER_FLAG_ALLOW_SCIENTIFIC);
        return $v2 === '' ? null : (float)$v2;
    }


}
