<?php
declare(strict_types=1);

namespace Web\Controllers;

use Web\Db;
use Web\Auth;
use Web\Lib\Http;
use PDO;

final class MetricsIngestController
{
    public function __construct(private Db $db, private Auth $auth) {}

    /**
     * POST /api/v1/metrics:batchUpsert
     * [
     *   {"platform":"YOUTUBE","platform_source_id":"yt_demo_A","measure":"VIEWS_3S","value":1000,"measured_at":"2024-06-01T00:00:00"},
     *   ...
     * ]
     *
     * Écrit dans metric_snapshot (unique: source_video_id + snapshot_at).
     */
    public function batchUpsert(): void
    {
        [$code, $items] = Http::readJson();
        if ($code !== 200) { Http::json($items, $code); return; }
        if (!is_array($items) || empty($items)) {
            Http::json(['error'=>'bad_request','detail'=>'array attendu'], 400);
            return;
        }

        $missing = [];
        $upserted = 0;

        $this->db->tx(function(PDO $pdo) use ($items, &$missing, &$upserted) {
            $findSv = $pdo->prepare(
                "SELECT id FROM source_video WHERE platform = ? AND platform_source_id = ? LIMIT 1"
            );

            $insSnap = $pdo->prepare(
                "INSERT INTO metric_snapshot (source_video_id, snapshot_at, views_3s)
                 VALUES (:sid, :snap, :v3s)
                 ON DUPLICATE KEY UPDATE views_3s = VALUES(views_3s)"
            );

            foreach ($items as $m) {
                if (!is_array($m)) continue;

                $platform           = $m['platform']           ?? 'YOUTUBE';
                $platform_source_id = $m['platform_source_id'] ?? null;
                $measure            = $m['measure']            ?? null;
                $value              = $m['value']              ?? null;
                $measured_at        = $m['measured_at']        ?? ($m['snapshot_at'] ?? null);

                if (!$platform_source_id || !$measure || $value === null || !$measured_at) {
                    continue; // ignorer lignes incomplètes
                }

                // On ne gère pour l’instant que VIEWS_3S -> colonne views_3s
                if (strtoupper($measure) !== 'VIEWS_3S') {
                    continue;
                }

                $findSv->execute([$platform, $platform_source_id]);
                $sid = (int)($findSv->fetchColumn() ?: 0);
                if ($sid <= 0) {
                    $missing[] = $platform_source_id;
                    continue;
                }

                // Normalisation timestamp -> 'YYYY-MM-DD HH:MM:SS'
                $snap = (string)$measured_at;
                $snap = str_replace('T', ' ', rtrim($snap, 'Z'));
                if (strlen($snap) === 16) $snap .= ':00'; // HH:MM -> HH:MM:SS
                if (strlen($snap) === 10) $snap .= ' 00:00:00';

                $insSnap->execute([
                    ':sid' => $sid,
                    ':snap'=> $snap,
                    ':v3s' => (int)$value,
                ]);
                $upserted++;
            }
        });

        Http::json(['upserted'=>$upserted, 'missingSources'=>array_values(array_unique($missing))], 200);
    }
}
