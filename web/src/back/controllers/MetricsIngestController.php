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

            // ID vidéo (tolère plusieurs noms)
            $pvid = $s['platform_video_id']
                ?? $s['platformId']
                ?? $s['platform_source_id']
                ?? $s['externalId']
                ?? $s['id']
                ?? null;

            if ($pvid === null || $pvid === '') { $skipped++; continue; }
            $pvid = (string)$pvid;

            // Horodatage du snapshot (ISO). Si absent -> maintenant (UTC).
            $snapAt = $s['snapshot_at'] ?? $s['snapshotAt'] ?? null;
            if (!$snapAt) $snapAt = gmdate('c'); // ex: 2025-09-04T18:58:03Z

            // Vues : on accepte plusieurs champs. On remplit à la fois views_3s ET/OU views_native si dispo.
            $views3s     = $s['views_3s'] ?? null;
            $views30s    = $s['views_30s'] ?? null;
            $viewsNative = $s['views_native'] ?? $s['views'] ?? $s['viewCount'] ?? null;

            // Engagements
            $likes    = $s['likes'] ?? $s['likeCount'] ?? null;
            $comments = $s['comments'] ?? $s['commentCount'] ?? null;

            $norm[] = [
                'platform'          => $platform,
                'platform_video_id' => $pvid,
                'snapshot_at'       => $snapAt,
                // Les 3 clés ci-dessous existent maintenant quoi qu’envoie le batch
                'views_3s'          => is_null($views3s)     ? null : (int)$views3s,
                'views_30s'         => is_null($views30s)    ? null : (int)$views30s,
                'views_native'      => is_null($viewsNative) ? null : (int)$viewsNative,
                'likes'             => is_null($likes)       ? null : (int)$likes,
                'comments'          => is_null($comments)    ? null : (int)$comments,
            ];
        }

        // Petit log utile (1ère fois uniquement)
        error_log(sprintf(
            '[metrics:batchUpsert] recv=%d norm=%d skipped=%d sample=%s',
            count($in), count($norm), $skipped,
            json_encode($norm[0] ?? null, JSON_UNESCAPED_SLASHES)
        ));

        // --- Appel service --------------------------------------------------------
        $service = new MetricsIngestService($this->db);
        try {
            $result = $service->ingestBatch($norm);
            Http::json([
                'status' => 'ok',
                'ok'     => $result['ok'],
                'ko'     => $result['ko'],
                'items'  => $result['items'], // garde le détail du service si présent
                'skipped_pre' => $skipped
            ], 200);
        } catch (\Throwable $e) {
            error_log('[metrics:batchUpsert] ERROR: '.$e->getMessage());
            Http::json(['error'=>'internal_error', 'message'=>$e->getMessage()], 500);
        }
    }

}
